package com.ssok.server.domain.ai.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssok.server.domain.ai.dto.SemanticSearchRequest;
import com.ssok.server.domain.ai.dto.SummaryRequest;
import com.ssok.server.domain.bookmark.dto.BookmarkSaveRequest;
import com.ssok.server.domain.space.entity.Space;
import com.ssok.server.domain.space.entity.SpaceType;
import com.ssok.server.domain.space.repository.SpaceRepository;
import com.ssok.server.domain.user.entity.User;
import com.ssok.server.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SpaceRepository spaceRepository;

    private Long spaceId;

    @BeforeEach
    void setUp() {
        User owner = userRepository.save(
                User.builder().email("ai-test@ssok.com").password("encoded").name("tester").build());

        Space space = spaceRepository.save(
                Space.builder().name("AI 테스트 스페이스").type(SpaceType.PERSONAL).owner(owner).build());

        spaceId = space.getId();
    }

    @Test
    void 북마크_요약을_생성한다() throws Exception {
        Long bookmarkId = saveBookmark("https://spring.io");

        mockMvc.perform(post("/api/ai/summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SummaryRequest(bookmarkId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.bookmarkId").value(bookmarkId))
                .andExpect(jsonPath("$.data.summary").isNotEmpty());
    }

    @Test
    void 존재하지_않는_북마크를_요약하면_404를_반환한다() throws Exception {
        mockMvc.perform(post("/api/ai/summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SummaryRequest(999_999L))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 의미_기반_검색으로_연관된_북마크를_찾는다() throws Exception {
        saveBookmark("https://spring.io");
        saveBookmark("https://github.com");

        mockMvc.perform(post("/api/ai/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SemanticSearchRequest("spring"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].title").value("spring.io"))
                .andExpect(jsonPath("$.data[0].similarity").isNumber());
    }

    @Test
    void 검색어와_무관한_북마크는_결과에서_제외된다() throws Exception {
        saveBookmark("https://spring.io");

        mockMvc.perform(post("/api/ai/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SemanticSearchRequest("완전히무관한검색어xyz"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void 유사_사이트를_추천받으면_자기_자신은_제외된다() throws Exception {
        Long springId = saveBookmark("https://spring.io");
        Long githubId = saveBookmark("https://github.com");

        mockMvc.perform(get("/api/ai/recommend").param("bookmarkId", String.valueOf(springId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[?(@.bookmarkId==" + springId + ")]").isEmpty())
                .andExpect(jsonPath("$.data[?(@.bookmarkId==" + githubId + ")]").exists());
    }

    @Test
    void 존재하지_않는_북마크로_추천을_요청하면_404를_반환한다() throws Exception {
        mockMvc.perform(get("/api/ai/recommend").param("bookmarkId", "999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    private Long saveBookmark(String url) throws Exception {
        BookmarkSaveRequest request = new BookmarkSaveRequest(url, spaceId);

        String response = mockMvc.perform(post("/api/bookmarks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response).path("data").path("bookmarkId").asLong();
    }
}
