package com.ssok.server.domain.bookmark.event;

import com.ssok.server.common.config.AiServerProperties;
import com.ssok.server.common.exception.AiServerException;
import com.ssok.server.domain.ai.client.AiServerClient;
import com.ssok.server.domain.ai.client.dto.AiAnalyzeRequest;
import com.ssok.server.domain.bookmark.service.BookmarkAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 북마크가 커밋된 뒤에 AI 서버로 분석을 요청한다.
 * 커밋 전에 요청하면 AI 서버의 콜백이 아직 저장되지 않은 북마크를 찾을 수 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookmarkAnalysisEventListener {

    private final AiServerClient aiServerClient;
    private final BookmarkAnalysisService bookmarkAnalysisService;
    private final AiServerProperties aiServerProperties;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(BookmarkAnalysisRequestedEvent event) {
        try {
            aiServerClient.requestAnalysis(AiAnalyzeRequest.of(
                    event.bookmarkId(), event.url(), event.spaceId(), event.savedAt(),
                    aiServerProperties.callbackUrl(), event.analysisRequestId()));
        } catch (AiServerException e) {
            log.error("AI analysis request failed. bookmarkId={}", event.bookmarkId(), e);
            bookmarkAnalysisService.markFailed(event.bookmarkId(), event.analysisRequestId());
        }
    }
}
