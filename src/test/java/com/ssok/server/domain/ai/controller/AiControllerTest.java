package com.ssok.server.domain.ai.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssok.server.common.exception.AiServerException;
import com.ssok.server.common.security.JwtTokenProvider;
import com.ssok.server.domain.ai.client.AiServerClient;
import com.ssok.server.domain.ai.client.dto.AiSearchResponse.Result;
import com.ssok.server.domain.ai.dto.SemanticSearchRequest;
import com.ssok.server.domain.ai.dto.SummaryRequest;
import com.ssok.server.domain.bookmark.entity.Bookmark;
import com.ssok.server.domain.bookmark.repository.BookmarkRepository;
import com.ssok.server.domain.space.entity.Space;
import com.ssok.server.domain.space.entity.SpaceMember;
import com.ssok.server.domain.space.entity.SpaceRole;
import com.ssok.server.domain.space.entity.SpaceType;
import com.ssok.server.domain.space.repository.SpaceMemberRepository;
import com.ssok.server.domain.space.repository.SpaceRepository;
import com.ssok.server.domain.user.entity.User;
import com.ssok.server.domain.user.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Transactional
class AiControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SpaceRepository spaceRepository;

    @Autowired
    private SpaceMemberRepository spaceMemberRepository;

    @Autowired
    private BookmarkRepository bookmarkRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @MockitoBean
    private AiServerClient aiServerClient;

    private Bookmark spring;
    private Bookmark github;
    private Bookmark othersBookmark;
    private Space space;
    private Space othersSpace;

    @BeforeEach
    void setUp() {
        User owner = userRepository.save(
                User.builder().email("ai-test@ssok.com").password("encoded").name("tester").build());
        space = spaceRepository.save(
                Space.builder().name("AI 테스트 스페이스").type(SpaceType.PERSONAL).owner(owner).build());
        spaceMemberRepository.save(SpaceMember.builder().space(space).user(owner).role(SpaceRole.OWNER).build());

        User other = userRepository.save(
                User.builder().email("ai-other@ssok.com").password("encoded").name("other").build());
        othersSpace = spaceRepository.save(
                Space.builder().name("다른 사람 스페이스").type(SpaceType.PERSONAL).owner(other).build());
        spaceMemberRepository.save(
                SpaceMember.builder().space(othersSpace).user(other).role(SpaceRole.OWNER).build());
        othersBookmark = saveAnalyzedBookmark("https://secret.example", "secret", "남의 북마크", othersSpace);

        spring = saveAnalyzedBookmark("https://spring.io", "spring.io", "Java 웹 프레임워크", space);
        github = saveAnalyzedBookmark("https://github.com", "github.com", "코드 호스팅 서비스", space);

        String accessToken = jwtTokenProvider.generateAccessToken(owner.getId());
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .defaultRequest(get("/").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .build();
    }

    @Test
    void AI_요약을_요청하면_202와_PENDING_상태를_반환한다() throws Exception {
        mockMvc.perform(post("/api/ai/summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SummaryRequest(spring.getId()))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.bookmarkId").value(spring.getId()))
                .andExpect(jsonPath("$.data.analysisStatus").value("PENDING"));
    }

    @Test
    void 존재하지_않는_북마크를_요약하면_404를_반환한다() throws Exception {
        mockMvc.perform(post("/api/ai/summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SummaryRequest(999_999L))))
                .andExpect(status().isNotFound());
    }

    @Test
    void 의미_기반_검색은_AI_서버의_순서를_유지하고_없는_북마크는_제외한다() throws Exception {
        given(aiServerClient.search(argThat(request -> request.query().equals("자바 프레임워크")))).willReturn(List.of(
                new Result(github.getId(), 0.9),
                new Result(999_999L, 0.8),
                new Result(spring.getId(), 0.7)));

        mockMvc.perform(post("/api/ai/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SemanticSearchRequest("자바 프레임워크"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(2))
                .andExpect(jsonPath("$.data.results[0].bookmarkId").value(github.getId()))
                .andExpect(jsonPath("$.data.results[0].url").value("https://github.com"))
                .andExpect(jsonPath("$.data.results[0].similarity").value(0.9))
                .andExpect(jsonPath("$.data.results[1].bookmarkId").value(spring.getId()))
                .andExpect(jsonPath("$.data.results[1].summary").value("Java 웹 프레임워크"));
    }

    @Test
    void AI_서버_장애_시_502를_반환한다() throws Exception {
        given(aiServerClient.search(any())).willThrow(new AiServerException("down", null));

        mockMvc.perform(post("/api/ai/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SemanticSearchRequest("아무거나"))))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 유사_사이트를_추천받으면_자기_자신은_제외된다() throws Exception {
        given(aiServerClient.search(any())).willReturn(List.of(
                new Result(spring.getId(), 1.0),
                new Result(github.getId(), 0.6)));

        mockMvc.perform(get("/api/ai/recommend").param("bookmarkId", String.valueOf(spring.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].bookmarkId").value(github.getId()));
    }

    @Test
    void 분석이_끝나지_않은_북마크는_추천_결과가_비어있다() throws Exception {
        Bookmark pending = bookmarkRepository.save(
                Bookmark.builder().url("https://example.com").title("example.com").space(spring.getSpace()).build());

        mockMvc.perform(get("/api/ai/recommend").param("bookmarkId", String.valueOf(pending.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void 남의_스페이스_북마크는_재분석을_요청할_수_없다() throws Exception {
        mockMvc.perform(post("/api/ai/summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SummaryRequest(othersBookmark.getId()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void 스페이스를_지정하지_않으면_내가_속한_스페이스_전체를_AI에_전달한다() throws Exception {
        given(aiServerClient.search(any())).willReturn(List.of());

        mockMvc.perform(post("/api/ai/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SemanticSearchRequest("자바"))))
                .andExpect(status().isOk());

        verify(aiServerClient).search(argThat(request -> request.query().equals("자바")
                && request.spaceIds().equals(List.of(space.getId()))));
    }

    @Test
    void 스페이스를_지정하면_그_스페이스만_AI에_전달한다() throws Exception {
        given(aiServerClient.search(any())).willReturn(List.of());

        mockMvc.perform(post("/api/ai/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SemanticSearchRequest("자바", space.getId()))))
                .andExpect(status().isOk());

        verify(aiServerClient).search(argThat(request -> request.query().equals("자바")
                && request.spaceIds().equals(List.of(space.getId()))));
    }

    @Test
    void 권한_없는_스페이스를_지정해_검색하면_403을_반환한다() throws Exception {
        mockMvc.perform(post("/api/ai/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SemanticSearchRequest("자바", othersSpace.getId()))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(aiServerClient);
    }

    @Test
    void 존재하지_않는_스페이스를_지정해_검색하면_404를_반환한다() throws Exception {
        mockMvc.perform(post("/api/ai/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SemanticSearchRequest("자바", 999_999L))))
                .andExpect(status().isNotFound());
    }

    @Test
    void AI가_범위_밖_북마크를_돌려줘도_결과에서_제외한다() throws Exception {
        given(aiServerClient.search(any())).willReturn(List.of(
                new Result(othersBookmark.getId(), 0.99),
                new Result(spring.getId(), 0.7)));

        mockMvc.perform(post("/api/ai/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SemanticSearchRequest("아무거나"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(1))
                .andExpect(jsonPath("$.data.results[0].bookmarkId").value(spring.getId()));
    }

    @Test
    void 속한_스페이스가_없으면_AI를_호출하지_않고_0건을_반환한다() throws Exception {
        User lonely = userRepository.save(
                User.builder().email("ai-lonely@ssok.com").password("encoded").name("lonely").build());
        String token = jwtTokenProvider.generateAccessToken(lonely.getId());

        mockMvc.perform(post("/api/ai/search")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SemanticSearchRequest("아무거나"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(0));

        verify(aiServerClient, never()).search(any());
    }

    @Test
    void 저장_날짜_조건을_AI에_전달하고_범위_밖_결과는_제외한다() throws Exception {
        Bookmark old = saveAnalyzedBookmark("https://old.example", "old", "오래된 글", space);
        jdbc.update("update bookmarks set created_at = ? where id = ?",
                java.time.LocalDateTime.of(2026, 10, 1, 12, 0), old.getId());
        entityManager.clear();
        given(aiServerClient.search(any())).willReturn(List.of(
                new Result(old.getId(), 0.95), new Result(spring.getId(), 0.7)));
        String today = java.time.LocalDate.now().toString();

        mockMvc.perform(post("/api/ai/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query": "글", "savedFrom": "2026-11-01", "savedTo": "%s"}
                                """.formatted(today)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(1))
                .andExpect(jsonPath("$.data.results[0].bookmarkId").value(spring.getId()));

        String expectedFrom = com.ssok.server.domain.ai.client.dto.AiTimeFormat.format(
                java.time.LocalDate.of(2026, 11, 1).atStartOfDay());
        String expectedTo = com.ssok.server.domain.ai.client.dto.AiTimeFormat.format(
                java.time.LocalDate.now().plusDays(1).atStartOfDay());
        verify(aiServerClient).search(argThat(request ->
                request.savedFrom().equals(expectedFrom) && request.savedTo().equals(expectedTo)));
    }

    @Test
    void 날짜_조건이_없으면_AI에_보내지_않고_limit은_top_k로_전달한다() throws Exception {
        given(aiServerClient.search(any())).willReturn(List.of());

        mockMvc.perform(post("/api/ai/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\": \"글\", \"limit\": 20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(0));

        verify(aiServerClient).search(argThat(request ->
                request.topK() == 20 && request.savedFrom() == null && request.savedTo() == null));
    }

    @Test
    void 시작일이_종료일보다_늦거나_limit이_범위를_벗어나면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/ai/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\": \"글\", \"savedFrom\": \"2026-11-10\", \"savedTo\": \"2026-11-01\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/ai/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\": \"글\", \"limit\": 51}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(aiServerClient);
    }

    @Test
    void 남의_스페이스_북마크로_추천을_요청하면_403을_반환한다() throws Exception {
        mockMvc.perform(get("/api/ai/recommend").param("bookmarkId", String.valueOf(othersBookmark.getId())))
                .andExpect(status().isForbidden());
    }

    private Bookmark saveAnalyzedBookmark(String url, String title, String summary, Space space) {
        Bookmark bookmark = Bookmark.builder().url(url).title(title).space(space).build();
        bookmark.completeAnalysis(summary, "개발/기술", List.of("Dev"));
        return bookmarkRepository.save(bookmark);
    }
}
