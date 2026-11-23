package com.ssok.server.domain.ai.vector;

import com.ssok.server.common.exception.AiServerException;
import com.ssok.server.domain.ai.client.AiServerClient;
import com.ssok.server.domain.bookmark.repository.BookmarkRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 예약된 벡터 변경을 AI 서버에 전달한다.
 * AI의 삭제·갱신 API는 멱등이므로 즉시 시도와 재시도가 겹쳐도 안전하다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VectorSyncProcessor {

    private static final int RETRY_BATCH_SIZE = 50;

    private final AiServerClient aiServerClient;
    private final VectorSyncTaskRepository taskRepository;
    private final BookmarkRepository bookmarkRepository;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(VectorSyncRequestedEvent event) {
        process(taskRepository.findAllByBookmarkIdIn(event.bookmarkIds()));
    }

    @Scheduled(fixedDelayString = "${ai.server.vector-sync-retry-interval}",
            initialDelayString = "${ai.server.vector-sync-retry-interval}")
    public void retryDue() {
        process(taskRepository.findDue(LocalDateTime.now(), PageRequest.of(0, RETRY_BATCH_SIZE)));
    }

    private void process(List<VectorSyncTask> tasks) {
        for (VectorSyncTask task : tasks) {
            try {
                apply(task);
                taskRepository.deleteById(task.getId());
            } catch (AiServerException e) {
                task.recordFailure(e.getMessage() + causeMessage(e), LocalDateTime.now());
                taskRepository.save(task);
                log.warn("AI vector sync failed. bookmarkId={}, operation={}, attempts={}, nextAttemptAt={}",
                        task.getBookmarkId(), task.getOperation(), task.getAttempts(), task.getNextAttemptAt(), e);
            }
        }
    }

    private void apply(VectorSyncTask task) {
        switch (task.getOperation()) {
            case DELETE -> aiServerClient.deleteVector(task.getBookmarkId());
            case UPDATE_SPACE -> {
                Optional<Long> spaceId = bookmarkRepository.findSpaceIdById(task.getBookmarkId());
                // 그 사이 삭제된 북마크라면 삭제 작업이 따로 처리하므로 갱신할 필요가 없다.
                spaceId.ifPresent(id -> aiServerClient.updateVectorSpace(task.getBookmarkId(), id));
            }
        }
    }

    private String causeMessage(Throwable e) {
        return e.getCause() != null ? ": " + e.getCause().getMessage() : "";
    }
}
