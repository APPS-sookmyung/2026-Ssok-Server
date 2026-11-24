package com.ssok.server.domain.notification.service;

import com.ssok.server.common.exception.*;
import com.ssok.server.domain.bookmark.repository.BookmarkRepository;
import com.ssok.server.domain.notification.dto.*;
import com.ssok.server.domain.notification.entity.*;
import com.ssok.server.domain.notification.repository.NotificationRepository;
import com.ssok.server.domain.space.entity.SpaceInvitation;
import com.ssok.server.domain.space.repository.*;
import com.ssok.server.domain.user.repository.UserRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {
    private final NotificationRepository notifications;
    private final SpaceMemberRepository members;
    private final SpaceRepository spaces;
    private final SpaceInvitationRepository invitations;
    private final BookmarkRepository bookmarks;
    private final UserRepository users;

    public NotificationListResponse list(Long userId, boolean unreadOnly, int page, int size) {
        requireUser(userId);
        if (page < 0 || size < 1 || size > 100) throw new InvalidRequestException("page must be >= 0 and size must be 1..100");
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        var result = unreadOnly ? notifications.findByRecipientIdAndReadAtIsNull(userId, pageable)
                : notifications.findByRecipientId(userId, pageable);
        return new NotificationListResponse(result.getContent().stream().map(notification -> new NotificationResponse(
                notification.getId(), notification.getType(), notification.getType().message(), notification.getCreatedAt(),
                notification.getReadAt(), target(userId, notification))).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    public long unreadCount(Long userId) {
        requireUser(userId);
        return notifications.countByRecipientIdAndReadAtIsNull(userId);
    }

    @Transactional
    public void read(Long userId, Long notificationId) {
        requireUser(userId);
        if (!notifications.existsByIdAndRecipientId(notificationId, userId)) throw new NotificationNotFoundException();
        notifications.markRead(notificationId, userId, Instant.now());
    }

    @Transactional
    public int readAll(Long userId) {
        requireUser(userId);
        return notifications.markAllRead(userId, Instant.now());
    }

    private NotificationResponse.Target target(Long userId, Notification n) {
        if (n.getType() == NotificationType.INACTIVITY_REMINDER) {
            return new NotificationResponse.Target("HOME", true, null, null, null, false);
        }
        if (n.getSpaceId() == null || !spaces.existsById(n.getSpaceId())) return NotificationResponse.Target.unavailable();
        boolean member = members.existsBySpaceIdAndUserId(n.getSpaceId(), userId);
        if (member) {
            if (n.getBookmarkId() != null) {
                boolean exists = bookmarks.findById(n.getBookmarkId())
                        .filter(b -> b.getSpace().getId().equals(n.getSpaceId())).isPresent();
                return exists ? new NotificationResponse.Target("BOOKMARK", true, n.getSpaceId(), n.getBookmarkId(), null, false)
                        : NotificationResponse.Target.unavailable();
            }
            return new NotificationResponse.Target("SPACE", true, n.getSpaceId(), null, null, false);
        }
        if (n.getType() == NotificationType.INVITATION_RECEIVED && n.getInvitationId() != null) {
            var user = users.findById(userId).orElse(null);
            var invitation = invitations.findByIdAndSpaceId(n.getInvitationId(), n.getSpaceId()).orElse(null);
            if (user != null && invitation != null && invitation.getEmail().equalsIgnoreCase(user.getEmail())
                    && invitation.getStatus() == SpaceInvitation.Status.PENDING && invitation.getExpiresAt().isAfter(Instant.now())) {
                return new NotificationResponse.Target("INVITATION", true, n.getSpaceId(), null, n.getInvitationId(), true);
            }
        }
        return NotificationResponse.Target.unavailable();
    }

    private void requireUser(Long userId) {
        if (userId == null || !users.existsById(userId)) throw new UnauthenticatedException("authentication required");
    }
}
