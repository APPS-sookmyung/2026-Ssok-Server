package com.ssok.server.domain.file.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** DB와 파일 시스템의 원자적 커밋이 불가능하므로, 삭제·롤백 복구 작업을 DB에 남긴다. */
@Entity
@Table(name = "file_deletion_jobs", indexes = @Index(name = "idx_file_job_due", columnList = "next_attempt_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FileDeletionJob {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 1024)
    private String storageId;
    @Column(nullable = false, length = 36)
    private String objectKey;
    @Column(nullable = false)
    private int attempts;
    @Column(nullable = false)
    private Instant nextAttemptAt;

    public FileDeletionJob(String storageId, String objectKey, Instant nextAttemptAt) {
        this.storageId = storageId;
        this.objectKey = objectKey;
        this.nextAttemptAt = nextAttemptAt;
    }

    public void failed() {
        attempts++;
        nextAttemptAt = Instant.now().plusSeconds(Math.min(3600, 30L << Math.min(attempts, 6)));
    }
}
