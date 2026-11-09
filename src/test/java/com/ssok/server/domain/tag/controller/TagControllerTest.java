package com.ssok.server.domain.tag.controller;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssok.server.common.security.JwtTokenProvider;
import com.ssok.server.domain.bookmark.entity.Bookmark;
import com.ssok.server.domain.bookmark.repository.BookmarkRepository;
import com.ssok.server.domain.space.entity.Space;
import com.ssok.server.domain.space.entity.SpaceType;
import com.ssok.server.domain.space.repository.SpaceRepository;
import com.ssok.server.domain.user.entity.User;
import com.ssok.server.domain.user.repository.UserRepository;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
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
    private UserRepository userRepository;

    @Autowired
    private SpaceRepository spaceRepository;

    @Autowired
    private BookmarkRepository bookmarkRepository;

    private Space space;

    @BeforeEach
    void setUp() {
        User owner = userRepository.save(
                User.builder().email("tag-test@ssok.com").password("encoded").name("tester").build());

        space = spaceRepository.save(
                Space.builder().name("태그 테스트 스페이스").type(SpaceType.PERSONAL).owner(owner).build());

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

    // 태그는 AI 분석 콜백으로 채워지므로, 분석이 끝난 북마크를 직접 저장한다.
    private void saveBookmark(String url) {
        String host = URI.create(url).getHost();
        String label = host.substring(0, host.indexOf('.'));
        String tag = Character.toUpperCase(label.charAt(0)) + label.substring(1);

        Bookmark bookmark = Bookmark.builder().url(url).title(host).space(space).build();
        bookmark.completeAnalysis(host + " 요약", "개발/기술", List.of(tag, "Bookmark"));
        bookmarkRepository.save(bookmark);
    }
}
