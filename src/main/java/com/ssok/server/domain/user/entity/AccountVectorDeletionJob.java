package com.ssok.server.domain.user.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "account_vector_deletion_jobs", uniqueConstraints = @UniqueConstraint(name = "uk_account_vector_space", columnNames = "space_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountVectorDeletionJob {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long spaceId;
    @Column(nullable = false) private int attempts;
    @Column(nullable = false) private Instant nextAttemptAt;
    public AccountVectorDeletionJob(Long spaceId) { this.spaceId = spaceId; nextAttemptAt = Instant.EPOCH; }
    public void failed() { attempts++; nextAttemptAt = Instant.now().plusSeconds(Math.min(3600, 30L << Math.min(attempts, 7))); }
}
