package com.ssok.server.domain.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import com.ssok.server.domain.ai.client.AiServerClient;
import com.ssok.server.domain.bookmark.entity.*;
import com.ssok.server.domain.bookmark.repository.BookmarkRepository;
import com.ssok.server.domain.library.entity.Library;
import com.ssok.server.domain.library.repository.LibraryRepository;
import com.ssok.server.domain.space.entity.*;
import com.ssok.server.domain.space.repository.*;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LibraryControllerTest {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private SpaceRepository spaces;
    @Autowired private SpaceMemberRepository members;
    @Autowired private LibraryRepository libraries;
    @Autowired private BookmarkRepository bookmarks;
    @Autowired private EntityManager em;
    @MockitoBean private AiServerClient ai;
    private User owner, editor, viewer, outsider;
    private Space space, other;
    private Library folder, second, foreign;
    private Bookmark bookmark;

    @BeforeEach
    void setup() {
        owner = user("owner"); editor = user("editor"); viewer = user("viewer"); outsider = user("outsider");
        space = spaces.save(Space.builder().name("team").type(SpaceType.TEAM).owner(owner).build());
        other = spaces.save(Space.builder().name("other").type(SpaceType.TEAM).owner(outsider).build());
        membership(space, owner, SpaceRole.OWNER); membership(space, editor, SpaceRole.EDITOR);
        membership(space, viewer, SpaceRole.VIEWER); membership(other, outsider, SpaceRole.OWNER);
        folder = libraries.save(new Library(space, "Design"));
        second = libraries.save(new Library(space, "Development"));
        foreign = libraries.save(new Library(other, "Private"));
        bookmark = Bookmark.builder().space(space).url("https://example.com/original").title("Original").build();
        bookmark.changeLibrary(folder);
        bookmark.completeAnalysis("original summary", "category", List.of("tag1", "tag2"));
        bookmark.recordVisit();
        bookmarks.saveAndFlush(bookmark);
        em.refresh(bookmark);
    }

    @Test
    void createRenameListDetailAndCounts() throws Exception {
        mvc.perform(post("/api/spaces/{s}/libraries", space.getId()).with(as(editor)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"  Research  \"}")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Research")).andExpect(jsonPath("$.data.bookmarkCount").value(0));
        mvc.perform(get("/api/spaces/{s}/libraries/{l}", space.getId(), folder.getId()).with(as(viewer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.bookmarkCount").value(1));
        mvc.perform(get("/api/spaces/{s}/libraries", space.getId()).with(as(viewer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].bookmarkCount").value(1));
        mvc.perform(patch("/api/spaces/{s}/libraries/{l}", space.getId(), folder.getId()).with(as(editor))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"UX\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("UX"));
    }

    @Test
    void deletingFolderPreservesBookmarkTagsAnalysisAndTimestamps() throws Exception {
        Long id = bookmark.getId();
        var savedAt = bookmark.getCreatedAt();
        var visitedAt = bookmark.getLastVisitedAt();
        em.flush(); em.clear();
        mvc.perform(delete("/api/spaces/{s}/libraries/{l}", space.getId(), folder.getId()).with(as(editor)))
                .andExpect(status().isOk());
        em.flush(); em.clear();
        assertThat(libraries.findById(folder.getId())).isEmpty();
        Bookmark remaining = bookmarks.findById(id).orElseThrow();
        assertThat(remaining.getLibrary()).isNull();
        assertThat(remaining.getTitle()).isEqualTo("Original");
        assertThat(remaining.getSummary()).isEqualTo("original summary");
        assertThat(remaining.getTags()).containsExactly("tag1", "tag2");
        assertThat(remaining.getAnalysisStatus()).isEqualTo(AnalysisStatus.SUCCESS);
        assertThat(remaining.getCreatedAt()).isEqualTo(savedAt);
        assertThat(remaining.getLastVisitedAt()).isEqualTo(visitedAt);
        assertThat(remaining.getVisitCount()).isEqualTo(1);
        mvc.perform(get("/api/bookmarks").with(as(viewer)).param("spaceId", space.getId().toString()).param("unclassified", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
        verifyNoInteractions(ai);
    }

    @Test
    void optionalFolderSaveAndMoveOrUnclassifyExistingBookmark() throws Exception {
        for (String field : List.of("", ",\"libraryId\":" + folder.getId())) {
            mvc.perform(post("/api/bookmarks").with(as(editor)).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"url\":\"https://example.com/new\",\"spaceId\":" + space.getId() + field + "}"))
                    .andExpect(status().isAccepted());
        }
        mvc.perform(patch("/api/bookmarks/{b}/library", bookmark.getId()).with(as(editor)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"libraryId\":" + second.getId() + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.libraryId").value(second.getId()));
        mvc.perform(get("/api/bookmarks/{b}", bookmark.getId()).with(as(viewer)))
                .andExpect(jsonPath("$.data.libraryId").value(second.getId()));
        mvc.perform(patch("/api/bookmarks/{b}/library", bookmark.getId()).with(as(editor)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"libraryId\":null}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.libraryId").isEmpty());
        mvc.perform(patch("/api/bookmarks/{b}/library", bookmark.getId()).with(as(editor)).contentType(MediaType.APPLICATION_JSON)
                .content("{}" )).andExpect(status().isBadRequest());
    }

    @Test
    void viewerCannotSearchOrMutateAndNonMemberCannotRead() throws Exception {
        mvc.perform(get("/api/spaces/{s}/libraries", space.getId()).with(as(viewer)).param("query", "Design"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/spaces/{s}/libraries", space.getId()).with(as(viewer)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Forbidden\"}")).andExpect(status().isForbidden());
        mvc.perform(patch("/api/spaces/{s}/libraries/{l}", space.getId(), folder.getId()).with(as(viewer)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Forbidden\"}")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/spaces/{s}/libraries/{l}", space.getId(), folder.getId()).with(as(viewer))).andExpect(status().isForbidden());
        mvc.perform(patch("/api/bookmarks/{b}/library", bookmark.getId()).with(as(viewer)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"libraryId\":null}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/spaces/{s}/libraries", space.getId()).with(as(outsider))).andExpect(status().isForbidden());
        mvc.perform(get("/api/spaces/{s}/libraries", space.getId())).andExpect(status().isUnauthorized());
    }

    @Test
    void crossSpaceLookupSaveAndMoveAreRejectedWithoutChanges() throws Exception {
        mvc.perform(get("/api/spaces/{s}/libraries/{l}", space.getId(), foreign.getId()).with(as(owner))).andExpect(status().isNotFound());
        mvc.perform(patch("/api/bookmarks/{b}/library", bookmark.getId()).with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"libraryId\":" + foreign.getId() + "}")).andExpect(status().isNotFound());
        mvc.perform(post("/api/bookmarks").with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://example.com/forbidden\",\"spaceId\":" + space.getId() + ",\"libraryId\":" + foreign.getId() + "}"))
                .andExpect(status().isNotFound());
        assertThat(bookmarks.countBySpaceId(space.getId())).isEqualTo(1);
        assertThat(bookmark.getLibrary().getId()).isEqualTo(folder.getId());
    }

    @Test
    void searchNamesValidateNamesAndDuplicates() throws Exception {
        mvc.perform(get("/api/spaces/{s}/libraries", space.getId()).with(as(editor)).param("query", "design"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
        mvc.perform(post("/api/spaces/{s}/libraries", space.getId()).with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\" design \"}")).andExpect(status().isConflict());
        for (String name : List.of(" ", "x".repeat(101))) {
            mvc.perform(post("/api/spaces/{s}/libraries", space.getId()).with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"" + name + "\"}")).andExpect(status().isBadRequest());
        }
        mvc.perform(patch("/api/spaces/{s}/libraries/{l}", space.getId(), second.getId()).with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Design\"}")).andExpect(status().isConflict());
        mvc.perform(post("/api/spaces/{s}/libraries", other.getId()).with(as(outsider)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Design\"}")).andExpect(status().isCreated());
    }

    @Test
    void listBookmarksByFolderAndValidatePagingAndFilters() throws Exception {
        mvc.perform(get("/api/spaces/{s}/libraries/{l}/bookmarks", space.getId(), folder.getId()).with(as(viewer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
        mvc.perform(get("/api/spaces/{s}/libraries/{l}/bookmarks", space.getId(), second.getId()).with(as(viewer)))
                .andExpect(jsonPath("$.data.totalElements").value(0));
        for (String sort : List.of("recent", "oldest", "title")) {
            mvc.perform(get("/api/spaces/{s}/libraries/{l}/bookmarks", space.getId(), folder.getId()).with(as(editor)).param("sort", sort))
                    .andExpect(status().isOk());
        }
        mvc.perform(get("/api/spaces/{s}/libraries/{l}/bookmarks", space.getId(), folder.getId()).with(as(viewer)).param("size", "101"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/bookmarks").with(as(viewer)).param("spaceId", space.getId().toString()).param("libraryId", folder.getId().toString()))
                .andExpect(jsonPath("$.data.totalElements").value(1));
        mvc.perform(get("/api/bookmarks").with(as(viewer)).param("spaceId", space.getId().toString()).param("libraryId", folder.getId().toString()).param("unclassified", "true"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletingSpaceRemovesLibrariesAndBookmarksWhileOtherSpaceSurvives() throws Exception {
        Long spaceId = space.getId(), libraryId = folder.getId(), bookmarkId = bookmark.getId();
        em.flush(); em.clear();
        mvc.perform(delete("/api/spaces/{s}", spaceId).with(as(owner))).andExpect(status().isOk());
        em.flush(); em.clear();
        assertThat(libraries.findById(libraryId)).isEmpty();
        assertThat(bookmarks.findById(bookmarkId)).isEmpty();
        assertThat(libraries.findById(foreign.getId())).isPresent();
    }

    private User user(String name) { return users.save(User.builder().name(name).email(name + "@libraries.test").password("test").build()); }
    private void membership(Space s, User u, SpaceRole role) { members.save(SpaceMember.builder().space(s).user(u).role(role).build()); }
    private RequestPostProcessor as(User user) { return authentication(new UsernamePasswordAuthenticationToken(user.getId(), null, List.of())); }
}
