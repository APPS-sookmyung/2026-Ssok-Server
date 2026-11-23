package com.ssok.server.domain.space.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "space_invitations", indexes = @Index(name = "idx_invitation_space_email", columnList = "space_id,email"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SpaceInvitation {
    public enum Status { PENDING, ACCEPTED, CANCELLED }
    public enum Delivery { PENDING, SENT, FAILED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "space_id", nullable = false)
    private Space space;
    @Column(nullable = false)
    private String email;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private SpaceRole role;
    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private Status status = Status.PENDING;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private Delivery delivery = Delivery.PENDING;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant expiresAt;

    public SpaceInvitation(Space space, String email, SpaceRole role, String tokenHash, Instant now, Instant expiresAt) {
        this.space = space;
        this.email = email;
        this.role = role;
        this.tokenHash = tokenHash;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    public void accept() { status = Status.ACCEPTED; }
    public void cancel() { status = Status.CANCELLED; }
    public void anonymizeRecipient(String replacement) {
        email = replacement;
        if (status == Status.PENDING) status = Status.CANCELLED;
    }
    public void reissue(String hash, Instant expiresAt) {
        this.tokenHash = hash;
        this.expiresAt = expiresAt;
        this.delivery = Delivery.PENDING;
    }
}
