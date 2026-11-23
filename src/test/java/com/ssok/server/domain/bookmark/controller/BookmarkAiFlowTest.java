package com.ssok.server.domain.bookmark.controller;

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
import com.ssok.server.domain.ai.vector.VectorSyncOperation;
import com.ssok.server.domain.ai.vector.VectorSyncTask;
import com.ssok.server.domain.ai.vector.VectorSyncTaskRepository;
import com.ssok.server.domain.bookmark.entity.AnalysisStatus;
import com.ssok.server.domain.bookmark.entity.Bookmark;
import com.ssok.server.domain.bookmark.service.BookmarkAnalysisService;
import com.ssok.server.domain.bookmark.dto.BookmarkSaveRequest;
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
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "ai.server.callback-secret=test-secret")
class BookmarkAiFlowTest {

    private static final String SECRET_HEADER = BookmarkAiCallbackController.CALLBACK_SECRET_HEADER;

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
    private BookmarkAnalysisService bookmarkAnalysisService;

    @Autowired
    private VectorSyncTaskRepository vectorSyncTaskRepository;

    @MockitoBean
    private AiServerClient aiServerClient;

    private Long spaceId;
    private Long ownerId;
    private Long outsiderId;

    @BeforeEach
    void setUp() {
        User owner = userRepository.save(
                User.builder().email("ai-flow-test@ssok.com").password("encoded").name("tester").build());

        Space space = spaceRepository.save(
                Space.builder().name("AI 연동 테스트 스페이스").type(SpaceType.PERSONAL).owner(owner).build());

        spaceMemberRepository.save(SpaceMember.builder().space(space).user(owner).role(SpaceRole.OWNER).build());

        User outsider = userRepository.save(
                User.builder().email("ai-flow-outsider@ssok.com").password("encoded").name("outsider").build());

        spaceId = space.getId();
        ownerId = owner.getId();
        outsiderId = outsider.getId();
    }

    @Test
    void 사이트를_저장하면_202와_PENDING_상태를_반환한다() throws Exception {
        mockMvc.perform(post("/api/bookmarks")
                        .with(loginAs(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new BookmarkSaveRequest("https://www.example.com/react-19-vs-18", spaceId))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.bookmarkId").exists())
                .andExpect(jsonPath("$.data.title").value("example.com"))
                .andExpect(jsonPath("$.data.analysisStatus").value("PENDING"));
    }

    @Test
    void 분석_성공_콜백을_받으면_제목과_요약과_태그가_저장된다() throws Exception {
        Long bookmarkId = saveBookmark();

        sendCallback(successBody(bookmarkId, currentRequestId(bookmarkId), "React 19 vs 18 비교"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("analysis result applied"));

        mockMvc.perform(get("/api/bookmarks/{bookmarkId}", bookmarkId).with(loginAs(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("React 19 vs 18 비교"))
                .andExpect(jsonPath("$.data.analysisStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.data.summary").value("React 19와 18의 렌더링 방식을 비교한 글입니다."))
                .andExpect(jsonPath("$.data.category").value("개발/기술"))
                .andExpect(jsonPath("$.data.tags.length()").value(3));
    }

    @Test
    void 제목이_비어_있으면_기존_제목을_유지한다() throws Exception {
        Long bookmarkId = saveBookmark();

        sendCallback(successBody(bookmarkId, currentRequestId(bookmarkId), "  ")).andExpect(status().isOk());

        assertThat(bookmarkRepository.findById(bookmarkId).orElseThrow().getTitle()).isEqualTo("example.com");
    }

    @Test
    void 분석_실패_콜백을_받으면_FAILED_상태가_된다() throws Exception {
        Long bookmarkId = saveBookmark();

        sendCallback(failureBody(bookmarkId, currentRequestId(bookmarkId))).andExpect(status().isOk());

        mockMvc.perform(get("/api/bookmarks/{bookmarkId}", bookmarkId).with(loginAs(ownerId)))
                .andExpect(jsonPath("$.data.analysisStatus").value("FAILED"));
    }

    @Test
    void 재분석_후_이전_요청의_콜백은_무시한다() throws Exception {
        Long bookmarkId = saveBookmark();
        String oldRequestId = currentRequestId(bookmarkId);

        mockMvc.perform(post("/api/ai/summary")
                        .with(loginAs(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookmarkId\": %d}".formatted(bookmarkId)))
                .andExpect(status().isAccepted());
        String newRequestId = currentRequestId(bookmarkId);
        assertThat(newRequestId).isNotEqualTo(oldRequestId);

        sendCallback(successBody(bookmarkId, oldRequestId, "이전 결과"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("analysis result ignored: ignored_stale"));

        Bookmark bookmark = bookmarkRepository.findById(bookmarkId).orElseThrow();
        assertThat(bookmark.getAnalysisStatus()).isEqualTo(AnalysisStatus.PENDING);
        assertThat(bookmark.getTitle()).isEqualTo("example.com");
    }

    @Test
    void 이미_반영한_결과가_다시_오면_무시한다() throws Exception {
        Long bookmarkId = saveBookmark();
        String requestId = currentRequestId(bookmarkId);
        sendCallback(successBody(bookmarkId, requestId, "첫 결과")).andExpect(status().isOk());

        sendCallback(failureBody(bookmarkId, requestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("analysis result ignored: ignored_duplicate"));

        Bookmark bookmark = bookmarkRepository.findById(bookmarkId).orElseThrow();
        assertThat(bookmark.getAnalysisStatus()).isEqualTo(AnalysisStatus.SUCCESS);
        assertThat(bookmark.getTitle()).isEqualTo("첫 결과");
    }

    @Test
    void 시간_초과로_FAILED_처리된_뒤_늦게_온_성공_결과는_반영한다() throws Exception {
        Long bookmarkId = saveBookmark();
        String requestId = currentRequestId(bookmarkId);
        bookmarkAnalysisService.failTimedOutAnalyses(Duration.ofSeconds(-1));
        assertThat(bookmarkRepository.findById(bookmarkId).orElseThrow().getAnalysisStatus())
                .isEqualTo(AnalysisStatus.FAILED);

        sendCallback(successBody(bookmarkId, requestId, "늦은 결과")).andExpect(status().isOk());

        assertThat(bookmarkRepository.findById(bookmarkId).orElseThrow().getAnalysisStatus())
                .isEqualTo(AnalysisStatus.SUCCESS);
    }

    @Test
    void 분석_중에_이동한_북마크는_결과_반영_후_벡터_스페이스_갱신을_예약한다() throws Exception {
        Long bookmarkId = saveBookmark();
        String requestId = currentRequestId(bookmarkId);
        Space other = spaceRepository.save(Space.builder().name("옮길 곳").type(SpaceType.TEAM)
                .owner(userRepository.findById(ownerId).orElseThrow()).build());
        spaceMemberRepository.save(SpaceMember.builder().space(other)
                .user(userRepository.findById(ownerId).orElseThrow()).role(SpaceRole.OWNER).build());

        mockMvc.perform(patch("/api/bookmarks/move").with(loginAs(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookmarkIds\": [%d], \"targetSpaceId\": %d}".formatted(bookmarkId, other.getId())))
                .andExpect(status().isOk());
        vectorSyncTaskRepository.deleteAll(vectorSyncTaskRepository.findAllByBookmarkIdIn(List.of(bookmarkId)));

        sendCallback(successBody(bookmarkId, requestId, "제목")).andExpect(status().isOk());

        assertThat(vectorSyncTaskRepository.findAllByBookmarkIdIn(List.of(bookmarkId)))
                .singleElement()
                .extracting(VectorSyncTask::getOperation)
                .isEqualTo(VectorSyncOperation.UPDATE_SPACE);
    }

    @Test
    void 콜백_시크릿이_틀리면_401을_반환한다() throws Exception {
        Long bookmarkId = saveBookmark();

        mockMvc.perform(post("/api/v1/bookmarks/ai-result")
                        .header(SECRET_HEADER, "wrong-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(failureBody(bookmarkId, currentRequestId(bookmarkId))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 콜백에_analysis_request_id가_없으면_400을_반환한다() throws Exception {
        Long bookmarkId = saveBookmark();

        sendCallback("""
                {"bookmark_id": %d, "status": "FAILED"}
                """.formatted(bookmarkId))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 삭제된_북마크에_대한_콜백은_200을_반환하고_벡터_삭제를_다시_예약한다() throws Exception {
        sendCallback(failureBody(999_999L, "req"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("analysis result ignored: ignored_deleted"));

        assertThat(vectorSyncTaskRepository.findAllByBookmarkIdIn(List.of(999_999L))).hasSize(1);
    }

    @Test
    void 스페이스_멤버가_아니면_북마크_상세_조회시_403을_반환하고_방문기록이_남지_않는다() throws Exception {
        Long bookmarkId = saveBookmark();

        mockMvc.perform(get("/api/bookmarks/{bookmarkId}", bookmarkId).with(loginAs(outsiderId)))
                .andExpect(status().isForbidden());

        assertThat(bookmarkRepository.findById(bookmarkId).orElseThrow().getVisitCount()).isZero();
    }

    @Test
    void 스페이스_멤버는_북마크_상세_조회시_방문수가_증가한다() throws Exception {
        Long bookmarkId = saveBookmark();

        mockMvc.perform(get("/api/bookmarks/{bookmarkId}", bookmarkId).with(loginAs(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.visitCount").value(1));
    }

    @Test
    void 스페이스_멤버가_아니면_북마크_저장시_403을_반환한다() throws Exception {
        long before = bookmarkRepository.count();

        mockMvc.perform(post("/api/bookmarks")
                        .with(loginAs(outsiderId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new BookmarkSaveRequest("https://example.com/outsider", spaceId))))
                .andExpect(status().isForbidden());

        assertThat(bookmarkRepository.count()).isEqualTo(before);
    }

    @Test
    void 로그인하지_않으면_북마크_저장시_401을_반환한다() throws Exception {
        mockMvc.perform(post("/api/bookmarks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new BookmarkSaveRequest("https://example.com/anonymous", spaceId))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 스페이스_멤버가_아니면_북마크_목록_조회시_403을_반환한다() throws Exception {
        saveBookmark();

        mockMvc.perform(get("/api/bookmarks").param("spaceId", String.valueOf(spaceId)).with(loginAs(outsiderId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void 스페이스_멤버는_북마크_목록을_조회할_수_있다() throws Exception {
        saveBookmark();

        mockMvc.perform(get("/api/bookmarks").param("spaceId", String.valueOf(spaceId)).with(loginAs(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void 스페이스_멤버가_아니면_북마크_삭제시_403을_반환하고_삭제되지_않는다() throws Exception {
        Long bookmarkId = saveBookmark();

        mockMvc.perform(delete("/api/bookmarks/{bookmarkId}", bookmarkId).with(loginAs(outsiderId)))
                .andExpect(status().isForbidden());

        assertThat(bookmarkRepository.existsById(bookmarkId)).isTrue();
    }

    @Test
    void 스페이스_멤버는_북마크를_삭제할_수_있다() throws Exception {
        Long bookmarkId = saveBookmark();

        mockMvc.perform(delete("/api/bookmarks/{bookmarkId}", bookmarkId).with(loginAs(ownerId)))
                .andExpect(status().isOk());

        assertThat(bookmarkRepository.existsById(bookmarkId)).isFalse();
        assertThat(vectorSyncTaskRepository.findAllByBookmarkIdIn(List.of(bookmarkId))).hasSize(1);
    }

    private ResultActions sendCallback(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/bookmarks/ai-result")
                .header(SECRET_HEADER, "test-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String currentRequestId(Long bookmarkId) {
        return bookmarkRepository.findById(bookmarkId).orElseThrow().getAnalysisRequestId();
    }

    private String successBody(Long bookmarkId, String requestId, String title) {
        return """
                {
                  "bookmark_id": %d,
                  "analysis_request_id": "%s",
                  "status": "SUCCESS",
                  "analysis": {
                    "title": "%s",
                    "summary": "React 19와 18의 렌더링 방식을 비교한 글입니다.",
                    "category": "개발/기술",
                    "tags": ["React", "React19", "Frontend"]
                  }
                }
                """.formatted(bookmarkId, requestId, title);
    }

    private String failureBody(Long bookmarkId, String requestId) {
        return """
                {"bookmark_id": %d, "analysis_request_id": "%s", "status": "FAILED"}
                """.formatted(bookmarkId, requestId);
    }

    private RequestPostProcessor loginAs(Long userId) {
        return authentication(new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    private Long saveBookmark() throws Exception {
        String response = mockMvc.perform(post("/api/bookmarks")
                        .with(loginAs(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new BookmarkSaveRequest("https://example.com/react-19-vs-18", spaceId))))
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response).path("data").path("bookmarkId").asLong();
    }
}
