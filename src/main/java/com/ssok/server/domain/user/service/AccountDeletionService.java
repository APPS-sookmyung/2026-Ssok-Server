package com.ssok.server.domain.user.service;

import com.ssok.server.common.exception.*;
import com.ssok.server.domain.file.entity.FilePurpose;
import com.ssok.server.domain.file.repository.StoredFileRepository;
import com.ssok.server.domain.file.service.FileCleanupService;
import com.ssok.server.domain.notification.repository.NotificationRepository;
import com.ssok.server.domain.space.entity.SpaceType;
import com.ssok.server.domain.space.repository.*;
import com.ssok.server.domain.space.service.*;
import com.ssok.server.domain.user.dto.AccountDeleteResponse;
import com.ssok.server.domain.user.entity.AccountVectorDeletionJob;
import com.ssok.server.domain.user.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
public class AccountDeletionService {
    private final UserRepository users;
    private final SpaceRepository spaces;
    private final SpaceMemberRepository members;
    private final SpaceInvitationRepository invitations;
    private final StoredFileRepository files;
    private final FileCleanupService fileCleanup;
    private final SpaceService spaceService;
    private final SpaceMemberService membership;
    private final NotificationRepository notifications;
    private final AccountVectorDeletionRepository vectorJobs;
    private final PasswordEncoder passwords;
    private final ApplicationEventPublisher events;
    private final EntityManager entityManager;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AccountDeleteResponse delete(Long userId, String password) {
        var initial = users.findById(userId).orElseThrow(() -> new UnauthenticatedException("authentication required"));
        if (!passwords.matches(password, initial.getPassword())) throw new InvalidCredentialsException("invalid password");
        // 기존 스페이스 작업과 동일하게 공간 → 사용자 순서로 잠근다.
        Set<Long> scope = scope(userId, initial.getEmail());
        scope.stream().sorted().forEach(id -> spaces.findLockedById(id));
        var user = users.findLockedById(userId).orElseThrow(() -> new UnauthenticatedException("authentication required"));
        entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
        if (!passwords.matches(password, user.getPassword())) throw new InvalidCredentialsException("invalid password");
        if (!scope.containsAll(scope(userId, user.getEmail()))) {
            throw new AccountDeletionConflictException("space membership changed; retry account deletion");
        }
        var owned = spaces.findAllByOwnerId(userId);
        if (owned.stream().anyMatch(s -> s.getType() == SpaceType.TEAM)) {
            throw new AccountDeletionConflictException("transfer ownership or delete owned team spaces first");
        }
        int taskCount = 0;
        for (var personal : owned) {
            var job = vectorJobs.saveAndFlush(new AccountVectorDeletionJob(personal.getId()));
            events.publishEvent(new AccountVectorCleanup.Requested(job.getId()));
            spaceService.delete(userId, personal.getId());
            taskCount++;
        }
        // 팀의 공유 파일은 보존하되 업로더 ID를 제거한다. 대화 첨부는 본인 소유만 삭제한다.
        for (var file : files.findAllByUploaderId(userId)) {
            if (file.getPurpose() == FilePurpose.CHAT_ATTACHMENT) {
                fileCleanup.enqueue(file);
                files.delete(file);
            } else file.anonymizeUploader();
        }
        for (var member : members.findAllByUserId(userId)) membership.leave(userId, member.getSpace().getId());
        String anonymousEmail = "deleted-" + UUID.randomUUID() + "@deleted.invalid";
        invitations.findAllByEmailIgnoreCase(user.getEmail()).forEach(invitation -> invitation.anonymizeRecipient(anonymousEmail));
        notifications.anonymizeActor(userId);
        // 설문은 JPA 컬렉션으로, 세션·수신 알림은 FK ON DELETE CASCADE로 정리한다.
        users.delete(user);
        users.flush();
        return new AccountDeleteResponse(true, taskCount);
    }

    private Set<Long> scope(Long userId, String email) {
        Set<Long> ids = new HashSet<>();
        spaces.findAllByOwnerId(userId).forEach(s -> ids.add(s.getId()));
        members.findAllByUserId(userId).forEach(m -> ids.add(m.getSpace().getId()));
        files.findAllByUploaderId(userId).forEach(f -> ids.add(f.getSpace().getId()));
        invitations.findAllByEmailIgnoreCase(email).forEach(i -> ids.add(i.getSpace().getId()));
        return ids;
    }
}
