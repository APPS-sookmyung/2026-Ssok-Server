package com.ssok.server.domain.space.service;

import com.ssok.server.common.exception.*;
import com.ssok.server.domain.space.dto.*;
import com.ssok.server.domain.space.entity.*;
import com.ssok.server.domain.space.repository.*;
import com.ssok.server.domain.user.entity.User;
import com.ssok.server.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SpaceInvitationService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final SpaceRepository spaces;
    private final SpaceMemberRepository members;
    private final SpaceInvitationRepository invitations;
    private final UserRepository users;
    private final SpaceAccessService access;
    private final ApplicationEventPublisher events;
    private final EntityManager entityManager;
    private final Duration ttl;
    private final String acceptUrl;

    public SpaceInvitationService(SpaceRepository spaces, SpaceMemberRepository members,
            SpaceInvitationRepository invitations, UserRepository users, SpaceAccessService access,
            ApplicationEventPublisher events, EntityManager entityManager,
            @Value("${invitation.ttl:72h}") Duration ttl,
            @Value("${invitation.accept-url:http://localhost:3000/invitations/accept}") String acceptUrl) {
        if (ttl.isNegative() || ttl.isZero() || acceptUrl.contains("#")) {
            throw new IllegalArgumentException("invalid invitation configuration");
        }
        this.spaces = spaces;
        this.members = members;
        this.invitations = invitations;
        this.users = users;
        this.access = access;
        this.events = events;
        this.entityManager = entityManager;
        this.ttl = ttl;
        this.acceptUrl = acceptUrl;
    }

    @Transactional
    public InvitationResponse create(Long userId, Long spaceId, InvitationCreateRequest request) {
        Space space = ownerSpace(userId, spaceId);
        SpaceRole role = request.role() == null ? SpaceRole.EDITOR : request.role();
        if (role == SpaceRole.OWNER) {
            throw new InvalidRequestException("invite role must be EDITOR or VIEWER");
        }
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        users.findByEmail(email).ifPresent(user -> {
            if (members.existsBySpaceIdAndUserId(spaceId, user.getId())) {
                throw new DuplicateSpaceMemberException("user is already a member");
            }
        });
        Instant now = Instant.now();
        if (invitations.findAllBySpaceIdAndEmailAndStatus(spaceId, email, SpaceInvitation.Status.PENDING).stream()
                .anyMatch(i -> i.getExpiresAt().isAfter(now))) {
            throw new InvitationConflictException("active invitation already exists");
        }
        String token = token();
        SpaceInvitation invitation = invitations.saveAndFlush(new SpaceInvitation(space, email, role, hash(token), now, now.plus(ttl)));
        return publish(invitation, token, userId);
    }

    @Transactional(readOnly = true)
    public List<InvitationResponse> list(Long userId, Long spaceId) {
        if (!spaces.existsById(spaceId)) throw new SpaceNotFoundException("space not found");
        access.requireOwner(userId, spaceId);
        return invitations.findAllBySpaceIdOrderByIdDesc(spaceId).stream().map(i -> InvitationResponse.from(i, null)).toList();
    }

    @Transactional
    public void cancel(Long userId, Long spaceId, Long invitationId) {
        ownerSpace(userId, spaceId);
        SpaceInvitation invitation = find(spaceId, invitationId);
        if (invitation.getStatus() == SpaceInvitation.Status.ACCEPTED) {
            throw new InvitationConflictException("accepted invitation cannot be cancelled; remove the member instead");
        }
        invitation.cancel();
    }

    @Transactional
    public InvitationResponse resend(Long userId, Long spaceId, Long invitationId) {
        ownerSpace(userId, spaceId);
        SpaceInvitation invitation = find(spaceId, invitationId);
        if (invitation.getStatus() != SpaceInvitation.Status.PENDING) {
            throw new InvitationConflictException("invitation is no longer pending");
        }
        if (invitations.findAllBySpaceIdAndEmailAndStatus(spaceId, invitation.getEmail(), SpaceInvitation.Status.PENDING).stream()
                .anyMatch(i -> !i.getId().equals(invitationId) && i.getExpiresAt().isAfter(Instant.now()))) {
            throw new InvitationConflictException("another active invitation already exists");
        }
        String token = token();
        invitation.reissue(hash(token), Instant.now().plus(ttl));
        invitations.flush();
        return publish(invitation, token, userId);
    }

    @Transactional
    public MemberDto accept(Long userId, String token) {
        if (userId == null) throw new UnauthenticatedException("authentication required");
        String hash = hash(token);
        SpaceInvitation invitation = invitations.findByTokenHash(hash)
                .orElseThrow(() -> new MemberNotFoundException("invitation not found"));
        spaces.findLockedById(invitation.getSpace().getId())
                .orElseThrow(() -> new SpaceNotFoundException("space not found"));
        // 토큰 조회 후 잠금을 기다리는 동안 수락·취소·재발급될 수 있으므로 최신 행을 읽는다.
        entityManager.refresh(invitation, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (!hash.equals(invitation.getTokenHash())) throw new MemberNotFoundException("invitation not found");
        User user = users.findById(userId).orElseThrow(() -> new UnauthenticatedException("authentication required"));
        if (!invitation.getEmail().equalsIgnoreCase(user.getEmail())) {
            throw new ForbiddenException("invitation belongs to a different email");
        }
        if (invitation.getStatus() != SpaceInvitation.Status.PENDING || !invitation.getExpiresAt().isAfter(Instant.now())) {
            throw new InvitationConflictException("invitation is expired or already used");
        }
        Long spaceId = invitation.getSpace().getId();
        if (members.existsBySpaceIdAndUserId(spaceId, userId)) throw new DuplicateSpaceMemberException("user is already a member");
        SpaceMember member = members.save(SpaceMember.builder().space(invitation.getSpace()).user(user).role(invitation.getRole()).build());
        invitation.accept();
        events.publishEvent(new com.ssok.server.domain.notification.event.SpaceNotificationEvent(
                com.ssok.server.domain.notification.entity.NotificationType.INVITATION_ACCEPTED, userId,
                invitation.getSpace().getOwner().getId(), spaceId, invitation.getId(), "INVITATION_ACCEPTED:" + invitation.getId()));
        return new MemberDto(member.getId(), userId, user.getName(), user.getEmail(), member.getRole().name());
    }

    private Space ownerSpace(Long userId, Long spaceId) {
        Space space = spaces.findLockedById(spaceId).orElseThrow(() -> new SpaceNotFoundException("space not found"));
        access.requireOwner(userId, spaceId);
        if (space.getType() != SpaceType.TEAM) throw new ForbiddenException("only team spaces support invitations");
        return space;
    }

    private SpaceInvitation find(Long spaceId, Long id) {
        return invitations.findByIdAndSpaceId(id, spaceId).orElseThrow(() -> new MemberNotFoundException("invitation not found"));
    }

    private InvitationResponse publish(SpaceInvitation invitation, String token, Long actorId) {
        // 프론트는 fragment에서 토큰을 읽고 POST body로 전달한다. URL 접근 로그에 토큰을 남기지 않는다.
        String url = acceptUrl + "#token=" + token;
        events.publishEvent(new InvitationMailRequested(invitation.getId(), invitation.getTokenHash(), invitation.getEmail(), url));
        users.findByEmail(invitation.getEmail()).ifPresent(recipient -> events.publishEvent(
                new com.ssok.server.domain.notification.event.SpaceNotificationEvent(
                        com.ssok.server.domain.notification.entity.NotificationType.INVITATION_RECEIVED, actorId, recipient.getId(),
                        invitation.getSpace().getId(), invitation.getId(), "INVITATION_RECEIVED:" + invitation.getId())));
        return InvitationResponse.from(invitation, url);
    }

    private static String token() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
