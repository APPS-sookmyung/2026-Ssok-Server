package com.ssok.server.domain.bookmark.controller;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
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
class BookmarkControllerTest {

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
                User.builder().email("bookmark-test@ssok.com").password("encoded").name("tester").build());

        Space space = spaceRepository.save(
                Space.builder().name("테스트 스페이스").type(SpaceType.PERSONAL).owner(owner).build());

        spaceId = space.getId();
    }

    @Test
    void 사이트를_저장하면_AI_분석_결과와_함께_201을_반환한다() throws Exception {
        BookmarkSaveRequest request = new BookmarkSaveRequest("https://spring.io", spaceId);

        mockMvc.perform(post("/api/bookmarks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.bookmarkId").exists())
                .andExpect(jsonPath("$.data.title").value("spring.io"))
                .andExpect(jsonPath("$.data.tags").isArray());
    }

    @Test
    void 잘못된_URL로_저장을_요청하면_400을_반환한다() throws Exception {
        BookmarkSaveRequest request = new BookmarkSaveRequest("not-a-url", spaceId);

        mockMvc.perform(post("/api/bookmarks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 존재하지_않는_스페이스로_저장을_요청하면_404를_반환한다() throws Exception {
        BookmarkSaveRequest request = new BookmarkSaveRequest("https://spring.io", 999_999L);

        mockMvc.perform(post("/api/bookmarks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 저장된_사이트_목록을_페이지로_조회한다() throws Exception {
        saveBookmark("https://spring.io");
        saveBookmark("https://github.com");

        mockMvc.perform(get("/api/bookmarks")
                        .param("spaceId", String.valueOf(spaceId))
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content.length()", greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data.totalElements", greaterThanOrEqualTo(2)));
    }

    @Test
    void 저장된_사이트를_조회하면_방문_횟수가_증가한다() throws Exception {
        Long bookmarkId = saveBookmark("https://spring.io");

        mockMvc.perform(get("/api/bookmarks/{bookmarkId}", bookmarkId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bookmarkId").value(bookmarkId))
                .andExpect(jsonPath("$.data.visitCount").value(1));

        mockMvc.perform(get("/api/bookmarks/{bookmarkId}", bookmarkId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.visitCount").value(2));
    }

    @Test
    void 존재하지_않는_북마크를_조회하면_404를_반환한다() throws Exception {
        mockMvc.perform(get("/api/bookmarks/{bookmarkId}", 999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 저장된_사이트를_삭제하면_목록에서_사라진다() throws Exception {
        Long bookmarkId = saveBookmark("https://spring.io");

        mockMvc.perform(delete("/api/bookmarks/{bookmarkId}", bookmarkId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));

        mockMvc.perform(get("/api/bookmarks/{bookmarkId}", bookmarkId))
                .andExpect(status().isNotFound());
    }

    @Test
    void 존재하지_않는_북마크를_삭제하면_404를_반환한다() throws Exception {
        mockMvc.perform(delete("/api/bookmarks/{bookmarkId}", 999_999L))
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
