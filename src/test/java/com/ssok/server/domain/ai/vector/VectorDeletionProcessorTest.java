package com.ssok.server.domain.ai.vector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ssok.server.common.exception.AiServerException;
import com.ssok.server.domain.ai.client.AiServerClient;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class VectorDeletionProcessorTest {

    @Mock
    private AiServerClient aiServerClient;

    @Mock
    private VectorDeletionTaskRepository taskRepository;

    @InjectMocks
    private VectorDeletionProcessor processor;

    @Test
    void 삭제에_성공하면_작업을_지운다() {
        VectorDeletionTask task = task(10L, 1L);
        given(taskRepository.findAllByBookmarkIdIn(List.of(1L))).willReturn(List.of(task));

        processor.handle(new VectorDeletionRequestedEvent(List.of(1L)));

        verify(aiServerClient).deleteVector(1L);
        verify(taskRepository).deleteById(10L);
    }

    @Test
    void 삭제에_실패하면_작업을_남기고_다음_시도_시각을_늦춘다() {
        VectorDeletionTask task = task(10L, 1L);
        given(taskRepository.findDue(any(), any())).willReturn(List.of(task));
        willThrow(new AiServerException("down", null)).given(aiServerClient).deleteVector(1L);

        LocalDateTime before = LocalDateTime.now();
        processor.retryDue();

        verify(taskRepository, never()).deleteById(any());
        verify(taskRepository).save(task);
        assertThat(task.getAttempts()).isEqualTo(1);
        assertThat(task.getNextAttemptAt()).isAfter(before);
        assertThat(task.getLastError()).contains("down");
    }

    @Test
    void 재시도_간격은_지수적으로_늘어나고_최대_1시간이다() {
        VectorDeletionTask task = new VectorDeletionTask(1L);
        LocalDateTime now = LocalDateTime.of(2026, 11, 23, 12, 0);

        task.recordFailure("e", now);
        assertThat(task.getNextAttemptAt()).isEqualTo(now.plusMinutes(1));
        task.recordFailure("e", now);
        assertThat(task.getNextAttemptAt()).isEqualTo(now.plusMinutes(2));
        for (int i = 0; i < 20; i++) {
            task.recordFailure("e", now);
        }
        assertThat(task.getNextAttemptAt()).isEqualTo(now.plusHours(1));
    }

    private VectorDeletionTask task(Long id, Long bookmarkId) {
        VectorDeletionTask task = new VectorDeletionTask(bookmarkId);
        ReflectionTestUtils.setField(task, "id", id);
        return task;
    }
}
