package com.ssok.server.domain.comment.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssok.server.domain.ai.client.AiServerClient;
import com.ssok.server.domain.bookmark.entity.Bookmark;
import com.ssok.server.domain.bookmark.repository.BookmarkRepository;
import com.ssok.server.domain.comment.event.CommentCreatedEvent;
import com.ssok.server.domain.space.entity.Space;
import com.ssok.server.domain.space.entity.SpaceMember;
import com.ssok.server.domain.space.entity.SpaceRole;
import com.ssok.server.domain.space.entity.SpaceType;
import com.ssok.server.domain.space.repository.SpaceMemberRepository;
import com.ssok.server.domain.space.repository.SpaceRepository;
import com.ssok.server.domain.user.entity.User;
import com.ssok.server.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@RecordApplicationEvents
class CommentControllerTest {

    @Autowired
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
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ApplicationEvents events;

    @MockitoBean
    private AiServerClient aiServerClient;

    private User saver;
    private User teammate;
    private User outsider;
    private Space team;
    private Bookmark bookmark;

    @BeforeEach
    void setUp() {
        saver = user("comment-saver@ssok.com", "저장자");
        teammate = user("comment-mate@ssok.com", "팀원");
        outsider = user("comment-out@ssok.com", "외부인");

        team = spaceRepository.save(Space.builder().name("팀").type(SpaceType.TEAM).owner(saver).build());
        spaceMemberRepository.save(SpaceMember.builder().space(team).user(saver).role(SpaceRole.OWNER).build());
        spaceMemberRepository.save(SpaceMember.builder().space(team).user(teammate).role(SpaceRole.MEMBER).build());

        bookmark = bookmarkRepository.save(
                Bookmark.builder().url("https://spring.io").title("spring.io").space(team).savedBy(saver).build());
    }

    @Test
    void 스페이스_멤버는_댓글을_작성하고_알림_이벤트가_발행된다() throws Exception {
        write(teammate, "  좋은 글이네요  ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.content").value("좋은 글이네요"))
                .andExpect(jsonPath("$.data.author.name").value("팀원"))
                .andExpect(jsonPath("$.data.mine").value(true))
                .andExpect(jsonPath("$.data.editedAt").doesNotExist());

        assertThat(events.stream(CommentCreatedEvent.class)).singleElement().satisfies(event -> {
            assertThat(event.bookmarkId()).isEqualTo(bookmark.getId());
            assertThat(event.spaceId()).isEqualTo(team.getId());
            assertThat(event.authorId()).isEqualTo(teammate.getId());
            assertThat(event.bookmarkSaverId()).isEqualTo(saver.getId());
        });
    }

    @Test
    void 스페이스_멤버가_아니면_댓글을_보거나_쓸_수_없다() throws Exception {
        write(outsider, "몰래").andExpect(status().isForbidden());

        mockMvc.perform(get(commentsUrl()).with(login(outsider)))
                .andExpect(status().isForbidden());
    }

    @Test
    void 댓글_목록은_작성_순이고_내_댓글_여부를_알려준다() throws Exception {
        write(saver, "첫 댓글");
        write(teammate, "두 번째");

        mockMvc.perform(get(commentsUrl()).with(login(saver)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].content").value("첫 댓글"))
                .andExpect(jsonPath("$.data.content[0].mine").value(true))
                .andExpect(jsonPath("$.data.content[1].content").value("두 번째"))
                .andExpect(jsonPath("$.data.content[1].mine").value(false));
    }

    @Test
    void 작성자는_댓글을_수정할_수_있고_수정_시각이_기록된다() throws Exception {
        Long commentId = commentId(write(teammate, "원래"));

        mockMvc.perform(patch(commentsUrl() + "/{id}", commentId).with(login(teammate))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\": \"고침\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("고침"))
                .andExpect(jsonPath("$.data.editedAt").exists());
    }

    @Test
    void 작성자가_아니면_수정_삭제할_수_없다() throws Exception {
        Long commentId = commentId(write(teammate, "내 댓글"));

        mockMvc.perform(patch(commentsUrl() + "/{id}", commentId).with(login(saver))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\": \"고침\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(commentsUrl() + "/{id}", commentId).with(login(saver)))
                .andExpect(status().isForbidden());
    }

    @Test
    void 작성자는_댓글을_삭제할_수_있다() throws Exception {
        Long commentId = commentId(write(teammate, "지울 댓글"));

        mockMvc.perform(delete(commentsUrl() + "/{id}", commentId).with(login(teammate)))
                .andExpect(status().isOk());

        mockMvc.perform(get(commentsUrl()).with(login(teammate)))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void 다른_북마크의_댓글_ID로_요청하면_404를_반환한다() throws Exception {
        Long commentId = commentId(write(teammate, "댓글"));
        Bookmark another = bookmarkRepository.save(
                Bookmark.builder().url("https://github.com").title("github").space(team).build());

        mockMvc.perform(delete("/api/bookmarks/{b}/comments/{c}", another.getId(), commentId).with(login(teammate)))
                .andExpect(status().isNotFound());
    }

    @Test
    void 빈_댓글이나_1000자를_넘는_댓글은_400을_반환한다() throws Exception {
        write(teammate, "   ").andExpect(status().isBadRequest());
        write(teammate, "가".repeat(1001)).andExpect(status().isBadRequest());
    }

    @Test
    void 북마크_카드에_댓글_수와_최근_댓글이_표시된다() throws Exception {
        write(saver, "첫 댓글");
        write(teammate, "가".repeat(60));

        mockMvc.perform(get("/api/bookmarks").param("spaceId", String.valueOf(team.getId())).with(login(saver)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].commentCount").value(2))
                .andExpect(jsonPath("$.data.content[0].latestComment.authorName").value("팀원"))
                .andExpect(jsonPath("$.data.content[0].latestComment.content").value("가".repeat(50) + "…"));

        mockMvc.perform(get("/api/bookmarks/{id}", bookmark.getId()).with(login(saver)))
                .andExpect(jsonPath("$.data.commentCount").value(2));
    }

    @Test
    void 댓글이_없는_북마크_카드는_0개와_null을_표시한다() throws Exception {
        mockMvc.perform(get("/api/bookmarks/recent").with(login(saver)))
                .andExpect(jsonPath("$.data[0].commentCount").value(0))
                .andExpect(jsonPath("$.data[0].latestComment").doesNotExist());
    }

    @Test
    void 북마크를_삭제하면_댓글도_함께_삭제된다() throws Exception {
        write(teammate, "댓글");
        Long bookmarkId = bookmark.getId();

        mockMvc.perform(post("/api/bookmarks/bulk-delete").with(login(saver))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookmarkIds\": [%d]}".formatted(bookmarkId)))
                .andExpect(status().isOk());
        entityManager.flush();

        assertThat(jdbc.queryForObject("select count(*) from comments where bookmark_id = ?", Long.class, bookmarkId))
                .isZero();
    }

    private ResultActions write(User author, String content) throws Exception {
        return mockMvc.perform(post(commentsUrl()).with(login(author))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new java.util.HashMap<>(java.util.Map.of("content", content)))));
    }

    private Long commentId(ResultActions result) throws Exception {
        String body = result.andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("commentId").asLong();
    }

    private String commentsUrl() {
        return "/api/bookmarks/" + bookmark.getId() + "/comments";
    }

    private User user(String email, String name) {
        return userRepository.save(User.builder().email(email).password("encoded").name(name).build());
    }

    private RequestPostProcessor login(User user) {
        return authentication(new UsernamePasswordAuthenticationToken(user.getId(), null, List.of()));
    }
}
