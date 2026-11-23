package com.ssok.server.domain.ai.vector;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 북마크 삭제·이동 시 AI 서버 벡터 변경을 예약한다.
 * 호출한 쪽의 트랜잭션에 참여해 DB 변경과 예약이 함께 커밋되며, 커밋 후 즉시 한 번 시도한다.
 * 실패한 건은 VectorSyncProcessor가 다시 시도한다.
 */
@Service
@RequiredArgsConstructor
public class VectorSyncService {

    private final VectorSyncTaskRepository taskRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueueDeletion(Collection<Long> bookmarkIds) {
        enqueue(bookmarkIds, VectorSyncOperation.DELETE);
    }

    /**
     * 이동한 북마크의 벡터 space_id를 갱신한다. 실제로 보낼 space_id는 처리 시점의 북마크 위치를 따른다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueueSpaceUpdate(Collection<Long> bookmarkIds) {
        enqueue(bookmarkIds, VectorSyncOperation.UPDATE_SPACE);
    }

    private void enqueue(Collection<Long> bookmarkIds, VectorSyncOperation operation) {
        List<Long> ids = bookmarkIds.stream().distinct().toList();
        if (ids.isEmpty()) {
            return;
        }

        Map<Long, VectorSyncTask> existing = taskRepository.findAllByBookmarkIdIn(ids).stream()
                .collect(Collectors.toMap(VectorSyncTask::getBookmarkId, Function.identity()));

        List<VectorSyncTask> created = ids.stream()
                .filter(id -> !existing.containsKey(id))
                .map(id -> new VectorSyncTask(id, operation))
                .toList();
        taskRepository.saveAll(created);

        // 이미 예약된 작업: 삭제는 이동보다 우선하고, 같은 종류면 그대로 둔다.
        if (operation == VectorSyncOperation.DELETE) {
            existing.values().stream()
                    .filter(task -> task.getOperation() != VectorSyncOperation.DELETE)
                    .forEach(VectorSyncTask::escalateToDelete);
        }

        eventPublisher.publishEvent(new VectorSyncRequestedEvent(ids));
    }
}
