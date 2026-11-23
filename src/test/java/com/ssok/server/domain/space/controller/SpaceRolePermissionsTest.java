package com.ssok.server.domain.space.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ssok.server.domain.ai.client.AiServerClient;
import com.ssok.server.domain.ai.client.dto.AiSearchResponse.Result;
import com.ssok.server.domain.bookmark.entity.AnalysisStatus;
import com.ssok.server.domain.bookmark.entity.Bookmark;
import com.ssok.server.domain.bookmark.repository.BookmarkRepository;
import com.ssok.server.domain.space.entity.*;
import com.ssok.server.domain.space.repository.*;
import com.ssok.server.domain.user.entity.User;
import com.ssok.server.domain.user.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SpaceRolePermissionsTest {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private SpaceRepository spaces;
    @Autowired private SpaceMemberRepository members;
    @Autowired private BookmarkRepository bookmarks;
    @MockitoBean private AiServerClient ai;

    private User owner;
    private User editor;
    private User viewer;
    private User outsider;
    private Space space;
    private SpaceMember ownerMembership;
    private SpaceMember editorMembership;
    private SpaceMember viewerMembership;
    private Bookmark bookmark;

    @BeforeEach
    void setUp() {
        owner = user("owner");
        editor = user("editor");
        viewer = user("viewer");
        outsider = user("outsider");
        space = spaces.save(Space.builder().name("shared team").type(SpaceType.TEAM).owner(owner).build());
        ownerMembership = membership(space, owner, SpaceRole.OWNER);
        editorMembership = membership(space, editor, SpaceRole.EDITOR);
        viewerMembership = membership(space, viewer, SpaceRole.VIEWER);
        bookmark = bookmark(space, "shared");
    }

    @ParameterizedTest
    @EnumSource(SpaceRole.class)
    void everyRoleCanReadSpaceAndBookmark(SpaceRole role) throws Exception {
        RequestPostProcessor actor = as(actor(role));
        mvc.perform(get("/api/spaces/{id}", space.getId()).with(actor)).andExpect(status().isOk());
        mvc.perform(get("/api/bookmarks").param("spaceId", space.getId().toString()).with(actor))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
        mvc.perform(get("/api/bookmarks/{id}", bookmark.getId()).with(actor)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @EnumSource(value = SpaceRole.class, names = {"OWNER", "EDITOR"})
    void ownerAndEditorCanCreateAndDeleteSharedBookmarks(SpaceRole role) throws Exception {
        mvc.perform(post("/api/bookmarks").with(as(actor(role))).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/new\",\"spaceId\":" + space.getId() + "}"))
                .andExpect(status().isAccepted());
        // 북마크 생성자와 다른 EDITOR도 같은 스페이스의 자료를 삭제할 수 있다.
        mvc.perform(delete("/api/bookmarks/{id}", bookmark.getId()).with(as(actor(role))))
                .andExpect(status().isOk());
        assertThat(bookmarks.findById(bookmark.getId())).isEmpty();
    }

    @Test
    void viewerCannotCreateDeleteOrManageSpace() throws Exception {
        long before = bookmarks.count();
        mvc.perform(post("/api/bookmarks").with(as(viewer)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/new\",\"spaceId\":" + space.getId() + "}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/bookmarks/{id}", bookmark.getId()).with(as(viewer)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/spaces/{id}", space.getId()).with(as(viewer)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/spaces/{id}/members", space.getId()).with(as(viewer)))
                .andExpect(status().isForbidden());
        assertThat(bookmarks.count()).isEqualTo(before);
        assertThat(bookmarks.findById(bookmark.getId())).isPresent();
        verifyNoInteractions(ai);
    }

    @Test
    void viewerCannotUseAnalysisSearchRecommendationOrTagSearch() throws Exception {
        mvc.perform(post("/api/ai/summary").with(as(viewer)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookmarkId\":" + bookmark.getId() + "}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/ai/search").with(as(viewer)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"shared\",\"spaceId\":" + space.getId() + "}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/ai/recommend").param("bookmarkId", bookmark.getId().toString()).with(as(viewer)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/tags").param("spaceId", space.getId().toString()).with(as(viewer)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/tags/shared/bookmarks").param("spaceId", space.getId().toString()).with(as(viewer)))
                .andExpect(status().isForbidden());
        assertThat(bookmarks.findById(bookmark.getId()).orElseThrow().getAnalysisStatus()).isEqualTo(AnalysisStatus.SUCCESS);
        verifyNoInteractions(ai);
    }

    @Test
    void globalSearchWithOnlyViewerMembershipReturnsNoResultsWithoutCallingAi() throws Exception {
        mvc.perform(post("/api/ai/search").with(as(viewer)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"shared\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(get("/api/tags").with(as(viewer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        verifyNoInteractions(ai);
    }

    @Test
    void aiAndTagResultsCannotLeakViewerOrForeignSpaces() throws Exception {
        Space viewed = spaces.save(Space.builder().name("view only").type(SpaceType.TEAM).owner(outsider).build());
        membership(viewed, editor, SpaceRole.VIEWER);
        Bookmark viewedBookmark = bookmark(viewed, "viewed");
        Space foreign = spaces.save(Space.builder().name("foreign").type(SpaceType.TEAM).owner(outsider).build());
        Bookmark foreignBookmark = bookmark(foreign, "foreign");
        when(ai.search(anyString(), anyInt(), anyList())).thenReturn(List.of(
                new Result(viewedBookmark.getId(), 0.99), new Result(foreignBookmark.getId(), 0.98),
                new Result(bookmark.getId(), 0.8)));
        mvc.perform(post("/api/ai/search").with(as(editor)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"shared\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].bookmarkId").value(bookmark.getId()));
        verify(ai).search("shared", 5, List.of(space.getId()));
        mvc.perform(get("/api/tags/shared/bookmarks").with(as(editor)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void ownerCanChangeRolesAndDowngradeImmediatelyRevokesAccess() throws Exception {
        mvc.perform(patch("/api/spaces/{spaceId}/members/{memberId}/role", space.getId(), editorMembership.getId())
                        .with(as(owner)).contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"VIEWER\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.role").value("VIEWER"));
        mvc.perform(delete("/api/bookmarks/{id}", bookmark.getId()).with(as(editor)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/ai/search").with(as(editor)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"shared\",\"spaceId\":" + space.getId() + "}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/bookmarks/{id}", bookmark.getId()).with(as(editor))).andExpect(status().isOk());
        mvc.perform(patch("/api/spaces/{spaceId}/members/{memberId}/role", space.getId(), viewerMembership.getId())
                        .with(as(owner)).contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"EDITOR\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.role").value("EDITOR"));
        mvc.perform(delete("/api/bookmarks/{id}", bookmark.getId()).with(as(viewer))).andExpect(status().isOk());
    }

    @ParameterizedTest
    @EnumSource(value = SpaceRole.class, names = {"EDITOR", "VIEWER"})
    void nonOwnerCannotChangeRolesInviteOrRemoveMembers(SpaceRole role) throws Exception {
        RequestPostProcessor actor = as(actor(role));
        mvc.perform(patch("/api/spaces/{spaceId}/members/{memberId}/role", space.getId(), viewerMembership.getId())
                        .with(actor).contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"EDITOR\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/spaces/{id}/invite", space.getId()).with(actor).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"outsider@roles.test\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/spaces/{id}/members/{memberId}", space.getId(), viewerMembership.getId()).with(actor))
                .andExpect(status().isForbidden());
        assertThat(members.findById(viewerMembership.getId()).orElseThrow().getRole()).isEqualTo(SpaceRole.VIEWER);
    }

    @Test
    void ownerCannotChangeOwnerOrAssignOwnerRole() throws Exception {
        mvc.perform(patch("/api/spaces/{spaceId}/members/{memberId}/role", space.getId(), ownerMembership.getId())
                        .with(as(owner)).contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"EDITOR\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/spaces/{spaceId}/members/{memberId}/role", space.getId(), viewerMembership.getId())
                        .with(as(owner)).contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"OWNER\"}"))
                .andExpect(status().isBadRequest());
        assertThat(ownerMembership.getRole()).isEqualTo(SpaceRole.OWNER);
    }

    @Test
    void invalidMissingAndLegacyRolesReturn400() throws Exception {
        for (String body : List.of("{\"role\":\"MEMBER\"}", "{\"role\":\"INVALID\"}", "{}", "{\"role\":null}")) {
            mvc.perform(patch("/api/spaces/{spaceId}/members/{memberId}/role", space.getId(), viewerMembership.getId())
                            .with(as(owner)).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void invitationDefaultsToEditorAndMissingTargetDoesNotChangeRoles() throws Exception {
        mvc.perform(post("/api/spaces/{id}/invite", space.getId()).with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"outsider@roles.test\"}"))
                .andExpect(status().isCreated());
        assertThat(members.findBySpaceIdAndUserId(space.getId(), outsider.getId()).orElseThrow().getRole())
                .isEqualTo(SpaceRole.EDITOR);
        mvc.perform(patch("/api/spaces/{spaceId}/members/{memberId}/role", space.getId(), Long.MAX_VALUE)
                        .with(as(owner)).contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"EDITOR\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void nonMembersAndAnonymousCannotChangeRolesOrReadBookmarks() throws Exception {
        mvc.perform(patch("/api/spaces/{spaceId}/members/{memberId}/role", space.getId(), viewerMembership.getId())
                        .with(as(outsider)).contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"EDITOR\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/bookmarks/{id}", bookmark.getId()).with(as(outsider))).andExpect(status().isForbidden());
        mvc.perform(patch("/api/spaces/{spaceId}/members/{memberId}/role", space.getId(), viewerMembership.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"EDITOR\"}"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @EnumSource(value = SpaceRole.class, names = {"EDITOR", "VIEWER"})
    void membersCanLeaveWithoutDeletingTeamMaterials(SpaceRole role) throws Exception {
        User user = actor(role);
        mvc.perform(delete("/api/spaces/{id}/members/me", space.getId()).with(as(user)))
                .andExpect(status().isOk());
        members.flush();
        assertThat(members.findBySpaceIdAndUserId(space.getId(), user.getId())).isEmpty();
        assertThat(bookmarks.existsById(bookmark.getId())).isTrue();
        mvc.perform(get("/api/bookmarks/{id}", bookmark.getId()).with(as(user)))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @EnumSource(value = SpaceRole.class, names = {"EDITOR", "VIEWER"})
    void ownerCanTransferThenLeaveAndNewOwnerCanManage(SpaceRole role) throws Exception {
        SpaceMember target = role == SpaceRole.EDITOR ? editorMembership : viewerMembership;
        User nextOwner = actor(role);
        mvc.perform(patch("/api/spaces/{id}/owner", space.getId()).with(as(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"memberId\":" + target.getId() + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.role").value("OWNER"));
        members.flush();
        assertThat(spaces.findById(space.getId()).orElseThrow().getOwner().getId()).isEqualTo(nextOwner.getId());
        assertThat(ownerMembership.getRole()).isEqualTo(SpaceRole.EDITOR);
        assertThat(members.findAllBySpaceId(space.getId()).stream().filter(m -> m.getRole() == SpaceRole.OWNER))
                .hasSize(1);
        mvc.perform(delete("/api/spaces/{id}", space.getId()).with(as(owner))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/spaces/{id}/members/me", space.getId()).with(as(owner))).andExpect(status().isOk());
        mvc.perform(delete("/api/spaces/{id}", space.getId()).with(as(nextOwner))).andExpect(status().isOk());
    }

    @Test
    void ownerCannotLeaveOrTransferToSelfAndInvalidTargetsDoNotChangeOwner() throws Exception {
        mvc.perform(delete("/api/spaces/{id}/members/me", space.getId()).with(as(owner))).andExpect(status().isForbidden());
        mvc.perform(patch("/api/spaces/{id}/owner", space.getId()).with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\":" + ownerMembership.getId() + "}")).andExpect(status().isBadRequest());
        Space other = spaces.save(Space.builder().name("other").type(SpaceType.TEAM).owner(outsider).build());
        SpaceMember foreign = membership(other, outsider, SpaceRole.OWNER);
        mvc.perform(patch("/api/spaces/{id}/owner", space.getId()).with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\":" + foreign.getId() + "}")).andExpect(status().isNotFound());
        assertThat(space.getOwner()).isEqualTo(owner);
        assertThat(ownerMembership.getRole()).isEqualTo(SpaceRole.OWNER);
    }

    @Test
    void personalSpaceCannotBeLeftOrTransferredButCanBeDeleted() throws Exception {
        Space personal = spaces.save(Space.builder().name("personal").type(SpaceType.PERSONAL).owner(owner).build());
        membership(personal, owner, SpaceRole.OWNER);
        mvc.perform(delete("/api/spaces/{id}/members/me", personal.getId()).with(as(owner))).andExpect(status().isForbidden());
        mvc.perform(patch("/api/spaces/{id}/owner", personal.getId()).with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\":" + editorMembership.getId() + "}")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/spaces/{id}", personal.getId()).with(as(owner))).andExpect(status().isOk());
    }

    @Test
    void lifecycleRequiresMembershipOwnerAndValidBody() throws Exception {
        for (User user : List.of(editor, viewer, outsider)) {
            mvc.perform(patch("/api/spaces/{id}/owner", space.getId()).with(as(user)).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"memberId\":" + editorMembership.getId() + "}")).andExpect(status().isForbidden());
        }
        mvc.perform(delete("/api/spaces/{id}/members/me", space.getId()).with(as(outsider))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/spaces/{id}/members/me", space.getId())).andExpect(status().isUnauthorized());
        for (String body : List.of("{}", "{\"memberId\":null}", "{\"memberId\":0}")) {
            mvc.perform(patch("/api/spaces/{id}/owner", space.getId()).with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                    .content(body)).andExpect(status().isBadRequest());
        }
    }

    private User actor(SpaceRole role) {
        return switch (role) { case OWNER -> owner; case EDITOR -> editor; case VIEWER -> viewer; };
    }

    private RequestPostProcessor as(User user) {
        return authentication(new UsernamePasswordAuthenticationToken(user.getId(), null, List.of()));
    }

    private User user(String name) {
        return users.save(User.builder().email(name + "@roles.test").name(name).password("test-only").build());
    }

    private SpaceMember membership(Space target, User user, SpaceRole role) {
        return members.save(SpaceMember.builder().space(target).user(user).role(role).build());
    }

    private Bookmark bookmark(Space target, String title) {
        Bookmark result = Bookmark.builder().space(target).url("https://example.com/" + title).title(title).build();
        result.completeAnalysis("summary", "category", List.of("shared", title));
        return bookmarks.saveAndFlush(result);
    }
}
