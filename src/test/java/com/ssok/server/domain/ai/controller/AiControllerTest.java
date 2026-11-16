package com.ssok.server.domain.ai.controller;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
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
import com.ssok.server.domain.space.entity.SpaceType;
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
    private BookmarkRepository bookmarkRepository;

    @MockitoBean
    private AiServerClient aiServerClient;

    private Bookmark spring;
    private Bookmark github;

    @BeforeEach
    void setUp() {
        User owner = userRepository.save(
                User.builder().email("ai-test@ssok.com").password("encoded").name("tester").build());
        Space space = spaceRepository.save(
                Space.builder().name("AI 테스트 스페이스").type(SpaceType.PERSONAL).owner(owner).build());

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
        given(aiServerClient.search(eq("자바 프레임워크"), anyInt())).willReturn(List.of(
                new Result(github.getId(), 0.9),
                new Result(999_999L, 0.8),
                new Result(spring.getId(), 0.7)));

        mockMvc.perform(post("/api/ai/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SemanticSearchRequest("자바 프레임워크"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].bookmarkId").value(github.getId()))
                .andExpect(jsonPath("$.data[0].url").value("https://github.com"))
                .andExpect(jsonPath("$.data[0].similarity").value(0.9))
                .andExpect(jsonPath("$.data[1].bookmarkId").value(spring.getId()))
                .andExpect(jsonPath("$.data[1].summary").value("Java 웹 프레임워크"));
    }

    @Test
    void AI_서버_장애_시_502를_반환한다() throws Exception {
        given(aiServerClient.search(anyString(), anyInt())).willThrow(new AiServerException("down", null));

        mockMvc.perform(post("/api/ai/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SemanticSearchRequest("아무거나"))))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 유사_사이트를_추천받으면_자기_자신은_제외된다() throws Exception {
        given(aiServerClient.search(anyString(), anyInt())).willReturn(List.of(
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

    private Bookmark saveAnalyzedBookmark(String url, String title, String summary, Space space) {
        Bookmark bookmark = Bookmark.builder().url(url).title(title).space(space).build();
        bookmark.completeAnalysis(summary, "개발/기술", List.of("Dev"));
        return bookmarkRepository.save(bookmark);
    }
}
