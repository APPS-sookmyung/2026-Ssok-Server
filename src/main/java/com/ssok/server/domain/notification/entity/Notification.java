package com.ssok.server.domain.notification.entity;

import com.ssok.server.domain.user.entity.User;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "notifications", uniqueConstraints = @UniqueConstraint(name = "uk_notification_event", columnNames = {"recipient_id", "event_key"}),
        indexes = @Index(name = "idx_notification_recipient_read", columnList = "recipient_id,read_at,created_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User recipient;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40, columnDefinition = "varchar(40)")
    private NotificationType type;
    @Column(nullable = false, length = 200)
    private String eventKey;
    private Long actorId;
    // 리소스가 삭제되어도 알림은 남기되 이동 권한은 반환 시 재검사한다.
    private Long spaceId;
    private Long bookmarkId;
    private Long invitationId;
    @CreationTimestamp @Column(nullable = false, updatable = false)
    private Instant createdAt;
    private Instant readAt;

    public Notification(User recipient, NotificationType type, String eventKey, Long actorId,
            Long spaceId, Long bookmarkId, Long invitationId) {
        this.recipient = recipient;
        this.type = type;
        this.eventKey = eventKey;
        this.actorId = actorId;
        this.spaceId = spaceId;
        this.bookmarkId = bookmarkId;
        this.invitationId = invitationId;
    }
}
