package com.ssok.server.domain.notification.repository;

import com.ssok.server.domain.notification.entity.Notification;
import java.time.Instant;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    @Modifying
    @Query("update Notification n set n.actorId = null where n.actorId = :userId")
    int anonymizeActor(@Param("userId") Long userId);
    @Modifying
    @Query(value = """
            insert into notifications (recipient_id, type, event_key, actor_id, space_id, bookmark_id, invitation_id, created_at)
            values (:userId, :type, :key, :actorId, :spaceId, :bookmarkId, :invitationId, :now)
            on duplicate key update event_key = event_key
            """, nativeQuery = true)
    int insertOnce(@Param("userId") Long userId, @Param("type") String type, @Param("key") String key,
            @Param("actorId") Long actorId, @Param("spaceId") Long spaceId, @Param("bookmarkId") Long bookmarkId,
            @Param("invitationId") Long invitationId, @Param("now") Instant now);

    Page<Notification> findByRecipientId(Long recipientId, Pageable pageable);
    Page<Notification> findByRecipientIdAndReadAtIsNull(Long recipientId, Pageable pageable);
    long countByRecipientIdAndReadAtIsNull(Long recipientId);
    boolean existsByRecipientIdAndEventKey(Long recipientId, String eventKey);
    boolean existsByIdAndRecipientId(Long id, Long recipientId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Notification n set n.readAt = :now where n.id = :id and n.recipient.id = :userId and n.readAt is null")
    int markRead(@Param("id") Long id, @Param("userId") Long userId, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Notification n set n.readAt = :now where n.recipient.id = :userId and n.readAt is null")
    int markAllRead(@Param("userId") Long userId, @Param("now") Instant now);
}
