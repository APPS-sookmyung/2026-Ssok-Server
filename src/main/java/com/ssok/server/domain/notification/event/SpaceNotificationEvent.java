package com.ssok.server.domain.notification.event;

import com.ssok.server.domain.notification.entity.NotificationType;
import java.util.UUID;

/** 권한 검증을 마친 도메인 서비스만 트랜잭션 안에서 발행한다. */
public record SpaceNotificationEvent(NotificationType type, Long actorId, Long recipientId,
        Long spaceId, Long invitationId, String eventKey) {
    public static SpaceNotificationEvent member(NotificationType type, Long actorId, Long recipientId, Long spaceId) {
        return new SpaceNotificationEvent(type, actorId, recipientId, spaceId, null, type + ":" + UUID.randomUUID());
    }
}
