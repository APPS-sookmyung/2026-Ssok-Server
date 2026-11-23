package com.ssok.server.domain.ai.vector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ssok.server.common.exception.AiServerException;
import com.ssok.server.domain.ai.client.AiServerClient;
import com.ssok.server.domain.bookmark.repository.BookmarkRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class VectorSyncProcessorTest {

    @Mock
    private AiServerClient aiServerClient;

    @Mock
    private VectorSyncTaskRepository taskRepository;

    @Mock
    private BookmarkRepository bookmarkRepository;

    @InjectMocks
    private VectorSyncProcessor processor;

    @Test
    void 삭제에_성공하면_작업을_지운다() {
        VectorSyncTask task = task(10L, 1L, VectorSyncOperation.DELETE);
        given(taskRepository.findAllByBookmarkIdIn(List.of(1L))).willReturn(List.of(task));

        processor.handle(new VectorSyncRequestedEvent(List.of(1L)));

        verify(aiServerClient).deleteVector(1L);
        verify(taskRepository).deleteById(10L);
    }

    @Test
    void 이동_갱신은_처리_시점의_스페이스_ID를_보낸다() {
        VectorSyncTask task = task(10L, 1L, VectorSyncOperation.UPDATE_SPACE);
        given(taskRepository.findAllByBookmarkIdIn(List.of(1L))).willReturn(List.of(task));
        given(bookmarkRepository.findSpaceIdById(1L)).willReturn(Optional.of(9L));

        processor.handle(new VectorSyncRequestedEvent(List.of(1L)));

        verify(aiServerClient).updateVectorSpace(1L, 9L);
        verify(taskRepository).deleteById(10L);
    }

    @Test
    void 이동_갱신_대상이_그_사이_삭제됐으면_AI를_호출하지_않고_작업을_지운다() {
        VectorSyncTask task = task(10L, 1L, VectorSyncOperation.UPDATE_SPACE);
        given(taskRepository.findAllByBookmarkIdIn(List.of(1L))).willReturn(List.of(task));
        given(bookmarkRepository.findSpaceIdById(1L)).willReturn(Optional.empty());

        processor.handle(new VectorSyncRequestedEvent(List.of(1L)));

        verify(aiServerClient, never()).updateVectorSpace(anyLong(), anyLong());
        verify(taskRepository).deleteById(10L);
    }

    @Test
    void 실패하면_작업을_남기고_다음_시도_시각을_늦춘다() {
        VectorSyncTask task = task(10L, 1L, VectorSyncOperation.DELETE);
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
        VectorSyncTask task = new VectorSyncTask(1L, VectorSyncOperation.DELETE);
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

    private VectorSyncTask task(Long id, Long bookmarkId, VectorSyncOperation operation) {
        VectorSyncTask task = new VectorSyncTask(bookmarkId, operation);
        ReflectionTestUtils.setField(task, "id", id);
        return task;
    }
}
