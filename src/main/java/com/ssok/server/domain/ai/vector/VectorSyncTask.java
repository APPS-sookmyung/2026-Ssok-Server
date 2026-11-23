package com.ssok.server.domain.ai.vector;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/**
 * AI 서버(pgvector)에 반영해야 할 북마크 벡터 변경.
 * 북마크 삭제·이동과 같은 트랜잭션에서 기록되므로 AI 호출이 실패하거나 서버가 재시작돼도 유실되지 않는다.
 * 북마크당 하나만 존재하며, 삭제는 이동보다 우선한다. AI 반영에 성공하면 행을 지운다.
 */
@Entity
@Table(name = "vector_sync_tasks", indexes = @Index(name = "idx_vector_sync_next_attempt",
        columnList = "nextAttemptAt"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VectorSyncTask {

    private static final Duration MAX_BACKOFF = Duration.ofHours(1);
    private static final int MAX_ERROR_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long bookmarkId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VectorSyncOperation operation;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(length = MAX_ERROR_LENGTH)
    private String lastError;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public VectorSyncTask(Long bookmarkId, VectorSyncOperation operation) {
        this.bookmarkId = bookmarkId;
        this.operation = operation;
        this.attempts = 0;
        this.nextAttemptAt = LocalDateTime.now();
    }

    /**
     * 이동 갱신이 남아 있는 북마크가 삭제되면 삭제로 바꾸고 즉시 다시 시도한다.
     */
    public void escalateToDelete() {
        this.operation = VectorSyncOperation.DELETE;
        this.attempts = 0;
        this.nextAttemptAt = LocalDateTime.now();
    }

    /**
     * 1분, 2분, 4분 ... 최대 1시간 간격으로 다시 시도한다.
     */
    public void recordFailure(String error, LocalDateTime now) {
        this.attempts++;
        long minutes = Math.min(1L << Math.min(attempts - 1, 10), MAX_BACKOFF.toMinutes());
        this.nextAttemptAt = now.plusMinutes(minutes);
        this.lastError = error == null || error.length() <= MAX_ERROR_LENGTH
                ? error
                : error.substring(0, MAX_ERROR_LENGTH);
    }
}
