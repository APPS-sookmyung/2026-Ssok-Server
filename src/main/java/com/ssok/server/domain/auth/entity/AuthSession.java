package com.ssok.server.domain.auth.entity;

import com.ssok.server.domain.user.entity.User;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "auth_sessions", indexes = @Index(name = "idx_auth_session_user", columnList = "user_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthSession {
    @Id @Column(length = 36)
    private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;
    @Column(nullable = false, length = 64)
    private String refreshHash;
    @Column(nullable = false)
    private Instant expiresAt;
    private Instant revokedAt;

    public AuthSession(String id, User user, String refreshHash, Instant expiresAt) {
        this.id = id; this.user = user; rotate(refreshHash, expiresAt);
    }
    public void rotate(String hash, Instant expiresAt) { this.refreshHash = hash; this.expiresAt = expiresAt; }
    public void revoke() { if (revokedAt == null) revokedAt = Instant.now(); }
    public boolean active() { return revokedAt == null && expiresAt.isAfter(Instant.now()); }
}
