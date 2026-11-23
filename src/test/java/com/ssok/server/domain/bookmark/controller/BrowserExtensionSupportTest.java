package com.ssok.server.domain.bookmark.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssok.server.domain.ai.client.AiServerClient;
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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/**
 * 브라우저 확장 프로그램 연결: CORS, 탭 제목으로 저장, 저장 여부 확인.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "app.cors.allowed-origins=chrome-extension://ssoktestextension, http://localhost:3000")
class BrowserExtensionSupportTest {

    private static final String EXTENSION_ORIGIN = "chrome-extension://ssoktestextension";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SpaceRepository spaceRepository;

    @Autowired
    private SpaceMemberRepository spaceMemberRepository;

    @Autowired
    private BookmarkRepository bookmarkRepository;

    @MockitoBean
    private AiServerClient aiServerClient;

    private User me;
    private Space personal;
    private Space othersSpace;

    @BeforeEach
    void setUp() {
        me = userRepository.save(User.builder().email("ext-me@ssok.com").password("encoded").name("나").build());
        User other = userRepository.save(
                User.builder().email("ext-other@ssok.com").password("encoded").name("남").build());
        personal = space("개인", me);
        othersSpace = space("남의 스페이스", other);
    }

    @Test
    void 허용된_확장_프로그램_origin의_사전_요청을_허용한다() throws Exception {
        mockMvc.perform(options("/api/bookmarks")
                        .header(HttpHeaders.ORIGIN, EXTENSION_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization, Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, EXTENSION_ORIGIN));
    }

    @Test
    void 허용되지_않은_origin의_사전_요청은_거부한다() throws Exception {
        mockMvc.perform(options("/api/bookmarks")
                        .header(HttpHeaders.ORIGIN, "chrome-extension://unknown")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 탭_제목을_함께_보내면_제목으로_저장한다() throws Exception {
        mockMvc.perform(post("/api/bookmarks").with(login())
                        .header(HttpHeaders.ORIGIN, EXTENSION_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "https://spring.io/guides", "spaceId": %d, "title": "  Spring | Guides  "}
                                """.formatted(personal.getId())))
                .andExpect(status().isAccepted())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, EXTENSION_ORIGIN))
                .andExpect(jsonPath("$.data.title").value("Spring | Guides"));
    }

    @Test
    void 이미_저장된_URL이면_저장된_위치를_알려준다() throws Exception {
        bookmarkRepository.save(Bookmark.builder().url("https://spring.io/guides/").title("가이드")
                .space(personal).build());
        bookmarkRepository.save(Bookmark.builder().url("https://spring.io/guides").title("남의 것")
                .space(othersSpace).build());

        mockMvc.perform(get("/api/bookmarks/lookup").param("url", "https://spring.io/guides").with(login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].title").value("가이드"))
                .andExpect(jsonPath("$.data[0].spaceName").value("개인"));
    }

    @Test
    void 저장되지_않은_URL이면_빈_목록을_반환한다() throws Exception {
        mockMvc.perform(get("/api/bookmarks/lookup").param("url", "https://new.site").with(login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    private Space space(String name, User member) {
        Space space = spaceRepository.save(Space.builder().name(name).type(SpaceType.PERSONAL).owner(member).build());
        spaceMemberRepository.save(SpaceMember.builder().space(space).user(member).role(SpaceRole.OWNER).build());
        return space;
    }

    private RequestPostProcessor login() {
        return authentication(new UsernamePasswordAuthenticationToken(me.getId(), null, List.of()));
    }
}
