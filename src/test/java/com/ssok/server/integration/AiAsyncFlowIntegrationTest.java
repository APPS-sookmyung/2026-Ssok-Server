package com.ssok.server.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssok.server.common.exception.AiServerException;
import com.ssok.server.domain.ai.client.AiServerClient;
import com.ssok.server.domain.ai.vector.VectorSyncTask;
import com.ssok.server.domain.ai.vector.VectorSyncTaskRepository;
import com.ssok.server.domain.bookmark.entity.AnalysisStatus;
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
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * 트랜잭션 커밋 이후에 비동기로 도는 흐름(분석 요청, 벡터 삭제·이동 동기화)을 실제로 커밋시켜 확인한다.
 * 다른 테스트는 테스트 트랜잭션이 롤백되어 AFTER_COMMIT 리스너가 실행되지 않는다.
 * 데이터가 실제로 저장되므로 테스트마다 직접 정리한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "ai.server.callback-secret=flow-secret",
        "ai.server.callback-base-url=https://api.ssok.test"
})
class AiAsyncFlowIntegrationTest {

    private static final Duration WAIT = Duration.ofSeconds(5);

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
    private VectorSyncTaskRepository vectorSyncTaskRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private AiServerClient aiServerClient;

    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> spaceIds = new ArrayList<>();
    private User owner;
    private Space personal;
    private Space team;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        owner = userRepository.save(
                User.builder().email("flow-" + suffix + "@ssok.com").password("encoded").name("flow").build());
        userIds.add(owner.getId());
        personal = space("개인", owner);
        team = space("팀", owner);
    }

    @AfterEach
    void cleanUp() {
        for (Long spaceId : spaceIds) {
            List<Long> bookmarkIds = jdbc.queryForList("select id from bookmarks where space_id = ?", Long.class, spaceId);
            for (Long bookmarkId : bookmarkIds) {
                jdbc.update("delete from bookmark_tags where bookmark_id = ?", bookmarkId);
            }
            jdbc.update("delete from bookmarks where space_id = ?", spaceId);
            jdbc.update("delete from space_members where space_id = ?", spaceId);
            jdbc.update("delete from spaces where id = ?", spaceId);
        }
        trackedBookmarkIds.forEach(id -> jdbc.update("delete from vector_sync_tasks where bookmark_id = ?", id));
        userIds.forEach(id -> jdbc.update("delete from users where id = ?", id));
    }

    private final List<Long> trackedBookmarkIds = new ArrayList<>();

    @Test
    void 저장이_커밋되면_AI에_분석을_요청하고_콜백으로_완료된다() throws Exception {
        Long bookmarkId = save("https://flow.example/a", personal);
        Bookmark saved = bookmarkRepository.findById(bookmarkId).orElseThrow();

        verify(aiServerClient, timeout(WAIT.toMillis())).requestAnalysis(argThat(request ->
                request.bookmarkId().equals(bookmarkId)
                        && request.spaceId().equals(personal.getId())
                        && request.analysisRequestId().equals(saved.getAnalysisRequestId())
                        && request.callbackUrl().equals("https://api.ssok.test/api/v1/bookmarks/ai-result")
                        && request.savedAt() != null));

        mockMvc.perform(post("/api/v1/bookmarks/ai-result")
                        .header("X-AI-Callback-Secret", "flow-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bookmark_id": %d, "analysis_request_id": "%s", "status": "SUCCESS",
                                 "analysis": {"title": "흐름 테스트", "summary": "요약", "category": "개발", "tags": ["A"]}}
                                """.formatted(bookmarkId, saved.getAnalysisRequestId())))
                .andExpect(status().isOk());

        Bookmark done = bookmarkRepository.findById(bookmarkId).orElseThrow();
        assertThat(done.getAnalysisStatus()).isEqualTo(AnalysisStatus.SUCCESS);
        assertThat(done.getTitle()).isEqualTo("흐름 테스트");
    }

    @Test
    void AI_분석_요청이_실패하면_비동기로_FAILED가_된다() throws Exception {
        willThrow(new AiServerException("down", null)).given(aiServerClient).requestAnalysis(any());

        Long bookmarkId = save("https://flow.example/fail", personal);

        awaitTrue(() -> bookmarkRepository.findById(bookmarkId).orElseThrow().getAnalysisStatus()
                == AnalysisStatus.FAILED);
    }

    @Test
    void 삭제가_커밋되면_AI_벡터를_지우고_작업을_정리한다() throws Exception {
        Long bookmarkId = save("https://flow.example/delete", personal);

        mockMvc.perform(delete("/api/bookmarks/{id}", bookmarkId).with(login())).andExpect(status().isOk());

        verify(aiServerClient, timeout(WAIT.toMillis())).deleteVector(bookmarkId);
        awaitTrue(() -> vectorSyncTaskRepository.findAllByBookmarkIdIn(List.of(bookmarkId)).isEmpty());
    }

    @Test
    void AI_벡터_삭제가_실패하면_작업이_남고_재시도가_예약된다() throws Exception {
        willThrow(new AiServerException("down", null)).given(aiServerClient).deleteVector(anyLong());
        Long bookmarkId = save("https://flow.example/retry", personal);
        LocalDateTime before = LocalDateTime.now();

        mockMvc.perform(delete("/api/bookmarks/{id}", bookmarkId).with(login())).andExpect(status().isOk());

        awaitTrue(() -> vectorSyncTaskRepository.findAllByBookmarkIdIn(List.of(bookmarkId)).stream()
                .anyMatch(task -> task.getAttempts() == 1));
        VectorSyncTask task = vectorSyncTaskRepository.findAllByBookmarkIdIn(List.of(bookmarkId)).get(0);
        assertThat(task.getNextAttemptAt()).isAfter(before);
        assertThat(task.getLastError()).contains("down");
        assertThat(vectorSyncTaskRepository.findAllByBookmarkIdIn(List.of(bookmarkId))).hasSize(1);
    }

    @Test
    void 이동이_커밋되면_AI_벡터의_스페이스를_갱신한다() throws Exception {
        Long bookmarkId = save("https://flow.example/move", personal);

        mockMvc.perform(patch("/api/bookmarks/move").with(login())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookmarkIds\": [%d], \"targetSpaceId\": %d}".formatted(bookmarkId, team.getId())))
                .andExpect(status().isOk());

        verify(aiServerClient, timeout(WAIT.toMillis())).updateVectorSpace(eq(bookmarkId), eq(team.getId()));
        awaitTrue(() -> vectorSyncTaskRepository.findAllByBookmarkIdIn(List.of(bookmarkId)).isEmpty());
    }

    @Test
    void 스페이스를_삭제하면_안의_북마크_벡터를_모두_지운다() throws Exception {
        Long first = save("https://flow.example/s1", team);
        Long second = save("https://flow.example/s2", team);

        mockMvc.perform(delete("/api/spaces/{id}", team.getId()).with(login())).andExpect(status().isOk());

        verify(aiServerClient, timeout(WAIT.toMillis())).deleteVector(first);
        verify(aiServerClient, timeout(WAIT.toMillis())).deleteVector(second);
        awaitTrue(() -> vectorSyncTaskRepository.findAllByBookmarkIdIn(List.of(first, second)).isEmpty());
    }

    private Long save(String url, Space space) throws Exception {
        String body = mockMvc.perform(post("/api/bookmarks").with(login())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\": \"%s\", \"spaceId\": %d}".formatted(url, space.getId())))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(body).path("data").path("bookmarkId").asLong();
        trackedBookmarkIds.add(id);
        return id;
    }

    private Space space(String name, User member) {
        Space space = spaceRepository.save(Space.builder().name(name).type(SpaceType.TEAM).owner(member).build());
        spaceMemberRepository.save(SpaceMember.builder().space(space).user(member).role(SpaceRole.OWNER).build());
        spaceIds.add(space.getId());
        return space;
    }

    private RequestPostProcessor login() {
        return authentication(new UsernamePasswordAuthenticationToken(owner.getId(), null, List.of()));
    }

    private void awaitTrue(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        assertThat(condition.getAsBoolean()).as("condition within %s", WAIT).isTrue();
    }
}
