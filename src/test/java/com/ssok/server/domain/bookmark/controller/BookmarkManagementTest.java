package com.ssok.server.domain.bookmark.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssok.server.domain.ai.client.AiServerClient;
import com.ssok.server.domain.ai.vector.VectorSyncOperation;
import com.ssok.server.domain.ai.vector.VectorSyncTask;
import com.ssok.server.domain.ai.vector.VectorSyncTaskRepository;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/**
 * 저장자 정보, 정렬, 최근 저장, 여러 개 삭제·이동.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BookmarkManagementTest {

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

    @Autowired
    private VectorSyncTaskRepository vectorSyncTaskRepository;

    @MockitoBean
    private AiServerClient aiServerClient;

    private User owner;
    private Space personal;
    private Space team;
    private Space othersSpace;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(
                User.builder().email("manage-owner@ssok.com").password("encoded").name("소유자").build());
        User other = userRepository.save(
                User.builder().email("manage-other@ssok.com").password("encoded").name("남").build());

        personal = space("개인", SpaceType.PERSONAL, owner);
        team = space("팀", SpaceType.TEAM, owner);
        othersSpace = space("남의 스페이스", SpaceType.PERSONAL, other);
    }

    @Test
    void 목록과_상세에_저장자와_스페이스_정보가_포함된다() throws Exception {
        Bookmark bookmark = bookmark(personal, "https://spring.io", 0);

        mockMvc.perform(get("/api/bookmarks").param("spaceId", id(personal)).with(login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].url").value("https://spring.io"))
                .andExpect(jsonPath("$.data.content[0].spaceName").value("개인"))
                .andExpect(jsonPath("$.data.content[0].savedBy.userId").value(owner.getId()))
                .andExpect(jsonPath("$.data.content[0].savedBy.name").value("소유자"))
                .andExpect(jsonPath("$.data.content[0].createdAt").exists());

        mockMvc.perform(get("/api/bookmarks/{id}", bookmark.getId()).with(login()))
                .andExpect(jsonPath("$.data.savedBy.name").value("소유자"))
                .andExpect(jsonPath("$.data.spaceId").value(personal.getId()));
    }

    @Test
    void 방문수순으로_정렬할_수_있다() throws Exception {
        bookmark(personal, "https://a.com", 1);
        bookmark(personal, "https://b.com", 5);
        bookmark(personal, "https://c.com", 3);

        mockMvc.perform(get("/api/bookmarks").param("spaceId", id(personal)).param("sort", "most-visited")
                        .with(login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].url").value("https://b.com"))
                .andExpect(jsonPath("$.data.content[1].url").value("https://c.com"))
                .andExpect(jsonPath("$.data.content[2].url").value("https://a.com"));
    }

    @Test
    void 기본_정렬은_최신_저장순이다() throws Exception {
        bookmark(personal, "https://first.com", 0);
        bookmark(personal, "https://second.com", 0);

        mockMvc.perform(get("/api/bookmarks").param("spaceId", id(personal)).with(login()))
                .andExpect(jsonPath("$.data.content[0].url").value("https://second.com"));
    }

    @Test
    void 지원하지_않는_정렬이나_페이지_크기는_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/bookmarks").param("spaceId", id(personal)).param("sort", "random")
                        .with(login()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/bookmarks").param("spaceId", id(personal)).param("size", "101")
                        .with(login()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/bookmarks").param("spaceId", id(personal)).param("page", "abc")
                        .with(login()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 최근_저장_목록은_내가_속한_모든_스페이스에서_최신순으로_가져온다() throws Exception {
        bookmark(personal, "https://old.com", 0);
        bookmark(othersSpace, "https://secret.com", 0);
        bookmark(team, "https://new.com", 0);

        mockMvc.perform(get("/api/bookmarks/recent").param("size", "10").with(login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].url").value("https://new.com"))
                .andExpect(jsonPath("$.data[0].spaceName").value("팀"))
                .andExpect(jsonPath("$.data[1].url").value("https://old.com"));
    }

    @Test
    void 여러_개를_삭제하면_모두_지워지고_벡터_삭제가_예약된다() throws Exception {
        Long first = bookmark(personal, "https://a.com", 0).getId();
        Long second = bookmark(team, "https://b.com", 0).getId();

        mockMvc.perform(post("/api/bookmarks/bulk-delete").with(login())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookmarkIds\": [%d, %d]}".formatted(first, second)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(2));

        assertThat(bookmarkRepository.findAllById(List.of(first, second))).isEmpty();
        assertThat(vectorSyncTaskRepository.findAllByBookmarkIdIn(List.of(first, second)))
                .extracting(VectorSyncTask::getOperation)
                .containsOnly(VectorSyncOperation.DELETE)
                .hasSize(2);
    }

    @Test
    void 권한_없는_북마크가_섞여_있으면_403이고_아무것도_삭제하지_않는다() throws Exception {
        Long mine = bookmark(personal, "https://a.com", 0).getId();
        Long others = bookmark(othersSpace, "https://b.com", 0).getId();

        mockMvc.perform(post("/api/bookmarks/bulk-delete").with(login())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookmarkIds\": [%d, %d]}".formatted(mine, others)))
                .andExpect(status().isForbidden());

        assertThat(bookmarkRepository.findAllById(List.of(mine, others))).hasSize(2);
    }

    @Test
    void 없는_북마크가_섞여_있으면_404이고_아무것도_삭제하지_않는다() throws Exception {
        Long mine = bookmark(personal, "https://a.com", 0).getId();

        mockMvc.perform(post("/api/bookmarks/bulk-delete").with(login())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookmarkIds\": [%d, 999999]}".formatted(mine)))
                .andExpect(status().isNotFound());

        assertThat(bookmarkRepository.existsById(mine)).isTrue();
    }

    @Test
    void 빈_목록으로_삭제를_요청하면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/bookmarks/bulk-delete").with(login())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookmarkIds\": []}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 다른_스페이스로_옮기면_벡터_스페이스_갱신이_예약된다() throws Exception {
        Long moving = bookmark(personal, "https://a.com", 0).getId();
        Long alreadyThere = bookmark(team, "https://b.com", 0).getId();

        mockMvc.perform(patch("/api/bookmarks/move").with(login())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookmarkIds\": [%d, %d], \"targetSpaceId\": %d}"
                                .formatted(moving, alreadyThere, team.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(1))
                .andExpect(jsonPath("$.data.bookmarkIds[0]").value(moving));

        assertThat(bookmarkRepository.findById(moving).orElseThrow().getSpace().getId()).isEqualTo(team.getId());
        assertThat(vectorSyncTaskRepository.findAllByBookmarkIdIn(List.of(moving, alreadyThere)))
                .singleElement()
                .satisfies(task -> {
                    assertThat(task.getBookmarkId()).isEqualTo(moving);
                    assertThat(task.getOperation()).isEqualTo(VectorSyncOperation.UPDATE_SPACE);
                });
    }

    @Test
    void 멤버가_아닌_스페이스로는_옮길_수_없다() throws Exception {
        Long mine = bookmark(personal, "https://a.com", 0).getId();

        mockMvc.perform(patch("/api/bookmarks/move").with(login())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookmarkIds\": [%d], \"targetSpaceId\": %d}"
                                .formatted(mine, othersSpace.getId())))
                .andExpect(status().isForbidden());

        assertThat(bookmarkRepository.findById(mine).orElseThrow().getSpace().getId())
                .isEqualTo(personal.getId());
    }

    @Test
    void 이동_후_삭제하면_예약된_갱신이_삭제로_바뀐다() throws Exception {
        Long id = bookmark(personal, "https://a.com", 0).getId();

        mockMvc.perform(patch("/api/bookmarks/move").with(login())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookmarkIds\": [%d], \"targetSpaceId\": %d}".formatted(id, team.getId())))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/bookmarks/bulk-delete").with(login())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookmarkIds\": [%d]}".formatted(id)))
                .andExpect(status().isOk());

        assertThat(vectorSyncTaskRepository.findAllByBookmarkIdIn(List.of(id)))
                .singleElement()
                .extracting(VectorSyncTask::getOperation)
                .isEqualTo(VectorSyncOperation.DELETE);
    }

    private Space space(String name, SpaceType type, User member) {
        Space space = spaceRepository.save(Space.builder().name(name).type(type).owner(member).build());
        spaceMemberRepository.save(SpaceMember.builder().space(space).user(member).role(SpaceRole.OWNER).build());
        return space;
    }

    private Bookmark bookmark(Space space, String url, int visits) {
        Bookmark bookmark = Bookmark.builder().url(url).title(url).space(space).savedBy(owner).build();
        for (int i = 0; i < visits; i++) {
            bookmark.recordVisit();
        }
        return bookmarkRepository.saveAndFlush(bookmark);
    }

    private String id(Space space) {
        return String.valueOf(space.getId());
    }

    private RequestPostProcessor login() {
        return authentication(new UsernamePasswordAuthenticationToken(owner.getId(), null, List.of()));
    }
}
