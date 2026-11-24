package com.ssok.server.domain.notification.service;

import com.ssok.server.common.exception.*;
import com.ssok.server.domain.bookmark.repository.BookmarkRepository;
import com.ssok.server.domain.notification.entity.NotificationType;
import com.ssok.server.domain.notification.event.*;
import com.ssok.server.domain.space.repository.*;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
public class NotificationEventListener {
    private final NotificationWriter writer;
    private final SpaceMemberRepository members;
    private final SpaceRepository spaces;
    private final BookmarkRepository bookmarks;
    private final Duration inactivityThreshold;

    public NotificationEventListener(NotificationWriter writer, SpaceMemberRepository members, SpaceRepository spaces,
            BookmarkRepository bookmarks,
            @Value("${notification.inactivity-threshold:30d}") Duration inactivityThreshold) {
        if (inactivityThreshold.isNegative() || inactivityThreshold.isZero()) throw new IllegalArgumentException("invalid inactivity threshold");
        this.writer = writer; this.members = members; this.spaces = spaces; this.bookmarks = bookmarks;
        this.inactivityThreshold = inactivityThreshold;
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void space(SpaceNotificationEvent event) {
        writer.write(event.recipientId(), event.type(), event.eventKey(), event.actorId(), event.spaceId(), null, event.invitationId());
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void comment(BookmarkCommentCreated event) {
        if (event.commentId() == null || event.commentId() < 1) throw new InvalidRequestException("invalid comment event");
        var bookmark = bookmarks.findById(event.bookmarkId()).orElseThrow(() -> new BookmarkNotFoundException("bookmark not found"));
        Long spaceId = bookmark.getSpace().getId();
        spaces.findLockedById(spaceId).orElseThrow(() -> new SpaceNotFoundException("space not found"));
        // 이벤트 발행 전 시작된 MySQL 스냅샷 대신 잠금 획득 후의 최신 멤버십을 사용한다.
        var recipients = members.findAllLockedBySpaceId(spaceId);
        boolean canWrite = recipients.stream().anyMatch(member -> member.getUser().getId().equals(event.actorId()) && member.getRole().canWrite());
        if (!canWrite) throw new ForbiddenException("comment author cannot write in this space");
        String key = "COMMENT_CREATED:" + bookmark.getId() + ":" + event.commentId();
        recipients.forEach(member -> writer.write(member.getUser().getId(),
                NotificationType.COMMENT_CREATED, key, event.actorId(), spaceId, bookmark.getId(), null));
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void returned(UserReturnedAfterAbsence event) {
        if (event.previousLoginAt() != null && !event.previousLoginAt().plus(inactivityThreshold).isAfter(Instant.now())) {
            writer.write(event.userId(), NotificationType.INACTIVITY_REMINDER, "RETURNED:" + event.previousLoginAt(), null, null, null, null);
        }
    }
}
