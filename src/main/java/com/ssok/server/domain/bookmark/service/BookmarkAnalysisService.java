package com.ssok.server.domain.bookmark.service;

import com.ssok.server.domain.ai.vector.VectorDeletionService;
import com.ssok.server.domain.bookmark.dto.AiAnalysisResultRequest;
import com.ssok.server.domain.bookmark.entity.AnalysisStatus;
import com.ssok.server.domain.bookmark.entity.Bookmark;
import com.ssok.server.domain.bookmark.repository.BookmarkRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 서버의 분석 결과를 북마크에 반영한다.
 *
 * <ul>
 *   <li>삭제된 북마크의 콜백은 무시하고, 다시 생겼을 수 있는 벡터의 삭제를 예약한다.</li>
 *   <li>최신 analysis_request_id가 아닌 콜백(이전 요청 결과)은 무시한다.</li>
 *   <li>이미 SUCCESS로 반영된 요청의 콜백은 중복으로 보고 무시한다.</li>
 *   <li>시간 초과로 FAILED 처리된 뒤 늦게 도착한 성공 결과는 반영한다.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookmarkAnalysisService {

    private final BookmarkRepository bookmarkRepository;
    private final VectorDeletionService vectorDeletionService;

    @Transactional
    public AnalysisResultOutcome applyResult(AiAnalysisResultRequest request) {
        // 같은 북마크의 콜백이 동시에 들어와도 한 번만 반영되도록 행 잠금을 건다.
        Optional<Bookmark> found = bookmarkRepository.findByIdForUpdate(request.bookmarkId());
        if (found.isEmpty()) {
            log.info("Ignoring AI result for deleted bookmark. bookmarkId={}", request.bookmarkId());
            // 삭제 이후 끝난 분석이 벡터를 다시 저장했을 수 있으므로 삭제를 한 번 더 예약한다.
            vectorDeletionService.enqueue(List.of(request.bookmarkId()));
            return AnalysisResultOutcome.IGNORED_DELETED;
        }

        Bookmark bookmark = found.get();
        if (!bookmark.isCurrentAnalysisRequest(request.analysisRequestId())) {
            log.info("Ignoring stale AI result. bookmarkId={}, requestId={}, currentRequestId={}",
                    bookmark.getId(), request.analysisRequestId(), bookmark.getAnalysisRequestId());
            return AnalysisResultOutcome.IGNORED_STALE;
        }

        if (bookmark.getAnalysisStatus() == AnalysisStatus.SUCCESS) {
            return AnalysisResultOutcome.IGNORED_DUPLICATE;
        }

        if (request.isSuccess()) {
            AiAnalysisResultRequest.Analysis analysis = request.analysis();
            bookmark.updateTitle(analysis.title());
            bookmark.completeAnalysis(analysis.summary(), analysis.category(), analysis.tags());
            return AnalysisResultOutcome.APPLIED;
        }

        if (bookmark.getAnalysisStatus() == AnalysisStatus.FAILED) {
            return AnalysisResultOutcome.IGNORED_DUPLICATE;
        }
        bookmark.failAnalysis();
        return AnalysisResultOutcome.APPLIED;
    }

    /**
     * AI 서버 호출 자체가 실패한 경우. 그 사이 재분석이 요청됐다면 새 요청은 건드리지 않는다.
     */
    @Transactional
    public void markFailed(Long bookmarkId, String analysisRequestId) {
        bookmarkRepository.findById(bookmarkId)
                .filter(bookmark -> bookmark.isCurrentAnalysisRequest(analysisRequestId))
                .filter(bookmark -> bookmark.getAnalysisStatus() == AnalysisStatus.PENDING)
                .ifPresent(Bookmark::failAnalysis);
    }

    /**
     * timeout 동안 콜백이 오지 않은 PENDING 분석을 FAILED로 바꾼다.
     *
     * @return FAILED로 바뀐 건수
     */
    @Transactional
    public int failTimedOutAnalyses(Duration timeout) {
        return bookmarkRepository.failPendingRequestedBefore(LocalDateTime.now().minus(timeout));
    }
}
