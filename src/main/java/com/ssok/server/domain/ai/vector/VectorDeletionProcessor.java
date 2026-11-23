package com.ssok.server.domain.ai.vector;

import com.ssok.server.common.exception.AiServerException;
import com.ssok.server.domain.ai.client.AiServerClient;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 예약된 벡터 삭제를 AI 서버에 전달한다.
 * AI 삭제 API는 멱등(반복 삭제에도 204)이므로 즉시 시도와 재시도가 겹쳐도 안전하다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VectorDeletionProcessor {

    private static final int RETRY_BATCH_SIZE = 50;

    private final AiServerClient aiServerClient;
    private final VectorDeletionTaskRepository taskRepository;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(VectorDeletionRequestedEvent event) {
        process(taskRepository.findAllByBookmarkIdIn(event.bookmarkIds()));
    }

    @Scheduled(fixedDelayString = "${ai.server.vector-deletion-retry-interval}",
            initialDelayString = "${ai.server.vector-deletion-retry-interval}")
    public void retryDue() {
        process(taskRepository.findDue(LocalDateTime.now(), PageRequest.of(0, RETRY_BATCH_SIZE)));
    }

    private void process(List<VectorDeletionTask> tasks) {
        for (VectorDeletionTask task : tasks) {
            try {
                aiServerClient.deleteVector(task.getBookmarkId());
                taskRepository.deleteById(task.getId());
            } catch (AiServerException e) {
                task.recordFailure(e.getMessage() + causeMessage(e), LocalDateTime.now());
                taskRepository.save(task);
                log.warn("AI vector deletion failed. bookmarkId={}, attempts={}, nextAttemptAt={}",
                        task.getBookmarkId(), task.getAttempts(), task.getNextAttemptAt(), e);
            }
        }
    }

    private String causeMessage(Throwable e) {
        return e.getCause() != null ? ": " + e.getCause().getMessage() : "";
    }
}
