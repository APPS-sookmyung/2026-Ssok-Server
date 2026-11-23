package com.ssok.server.domain.notification.service;

import com.ssok.server.domain.notification.entity.*;
import com.ssok.server.domain.notification.repository.NotificationRepository;
import com.ssok.server.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
public class NotificationWriter {
    private final NotificationRepository notifications;
    private final UserRepository users;

    @Transactional(propagation = Propagation.MANDATORY)
    public void write(Long recipientId, NotificationType type, String key, Long actorId, Long spaceId, Long bookmarkId, Long invitationId) {
        if (recipientId == null || recipientId.equals(actorId)) return;
        // MySQL/H2(MySQL mode)의 원자적 UPSERT. 중복 이벤트가 와도 읽음·생성 시각을 바꾸지 않는다.
        users.findById(recipientId).ifPresent(user -> notifications.insertOnce(recipientId, type.name(), key,
                actorId, spaceId, bookmarkId, invitationId, java.time.Instant.now()));
    }
}
