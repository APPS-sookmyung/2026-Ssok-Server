package com.ssok.server.domain.notification.dto;

import com.ssok.server.domain.notification.entity.NotificationType;
import java.time.Instant;

public record NotificationResponse(Long notificationId, NotificationType type, String message, Instant createdAt, Instant readAt,
        Target target) {
    /** kind는 NONE, SPACE, BOOKMARK, INVITATION, HOME. 프론트가 자신의 라우팅에 연결한다. */
    public record Target(String kind, boolean available, Long spaceId, Long bookmarkId, Long invitationId, boolean requiresInvitationToken) {
        public static Target unavailable() { return new Target("NONE", false, null, null, null, false); }
    }
}
