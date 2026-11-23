package com.ssok.server.domain.ai.vector;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 북마크 삭제 시 AI 서버 벡터 삭제를 예약한다.
 * 호출한 쪽의 삭제 트랜잭션에 참여해 삭제와 예약이 함께 커밋되며, 커밋 후 즉시 한 번 시도한다.
 * 실패한 건은 VectorDeletionRetryScheduler가 다시 시도한다.
 */
@Service
@RequiredArgsConstructor
public class VectorDeletionService {

    private final VectorDeletionTaskRepository taskRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(Collection<Long> bookmarkIds) {
        if (bookmarkIds.isEmpty()) {
            return;
        }

        Set<Long> alreadyQueued = taskRepository.findAllByBookmarkIdIn(bookmarkIds).stream()
                .map(VectorDeletionTask::getBookmarkId)
                .collect(Collectors.toSet());

        List<VectorDeletionTask> tasks = bookmarkIds.stream()
                .distinct()
                .filter(id -> !alreadyQueued.contains(id))
                .map(VectorDeletionTask::new)
                .toList();
        taskRepository.saveAll(tasks);

        eventPublisher.publishEvent(new VectorDeletionRequestedEvent(List.copyOf(bookmarkIds)));
    }
}
