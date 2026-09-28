package com.ssok.server.domain.tag.controller;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssok.server.common.security.JwtTokenProvider;
import com.ssok.server.domain.bookmark.dto.BookmarkSaveRequest;
import com.ssok.server.domain.space.entity.Space;
import com.ssok.server.domain.space.entity.SpaceType;
import com.ssok.server.domain.space.repository.SpaceRepository;
import com.ssok.server.domain.user.entity.User;
import com.ssok.server.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Transactional
class TagControllerTest {

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

    private Long spaceId;

    @BeforeEach
    void setUp() {
        User owner = userRepository.save(
                User.builder().email("tag-test@ssok.com").password("encoded").name("tester").build());

        Space space = spaceRepository.save(
                Space.builder().name("태그 테스트 스페이스").type(SpaceType.PERSONAL).owner(owner).build());

        spaceId = space.getId();

        String accessToken = jwtTokenProvider.generateAccessToken(owner.getId());
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .defaultRequest(get("/").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .build();
    }

    @Test
    void 태그_목록을_조회하면_북마크_수와_함께_반환된다() throws Exception {
        saveBookmark("https://spring.io");
        saveBookmark("https://github.com");

        mockMvc.perform(get("/api/tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[?(@.tagName=='Bookmark')].bookmarkCount").value(2))
                .andExpect(jsonPath("$.data[?(@.tagName=='Spring')].bookmarkCount").value(1))
                .andExpect(jsonPath("$.data[?(@.tagName=='Github')].bookmarkCount").value(1));
    }

    @Test
    void 특정_태그가_포함된_북마크_목록을_조회한다() throws Exception {
        saveBookmark("https://spring.io");
        saveBookmark("https://github.com");

        mockMvc.perform(get("/api/tags/{tagName}/bookmarks", "Spring")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tagName").value("Spring"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].title").value("spring.io"))
                .andExpect(jsonPath("$.data.content[0].tags[0]").value("Spring"));
    }

    @Test
    void 공통_태그로_조회하면_해당_태그가_붙은_모든_북마크가_반환된다() throws Exception {
        saveBookmark("https://spring.io");
        saveBookmark("https://github.com");

        mockMvc.perform(get("/api/tags/{tagName}/bookmarks", "Bookmark"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    void 존재하지_않는_태그를_조회하면_404를_반환한다() throws Exception {
        mockMvc.perform(get("/api/tags/{tagName}/bookmarks", "NoSuchTag"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 마지막_페이지를_넘어서_조회하면_빈_목록을_반환한다() throws Exception {
        saveBookmark("https://spring.io");

        mockMvc.perform(get("/api/tags/{tagName}/bookmarks", "Spring")
                        .param("page", "5")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }

    private void saveBookmark(String url) throws Exception {
        BookmarkSaveRequest request = new BookmarkSaveRequest(url, spaceId);

        mockMvc.perform(post("/api/bookmarks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }
}
