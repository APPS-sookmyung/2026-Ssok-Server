package com.ssok.server.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ssok.server.common.exception.ForbiddenException;
import com.ssok.server.domain.auth.dto.LoginRequest;
import com.ssok.server.domain.auth.service.AuthService;
import com.ssok.server.domain.bookmark.entity.Bookmark;
import com.ssok.server.domain.bookmark.repository.BookmarkRepository;
import com.ssok.server.domain.notification.entity.*;
import com.ssok.server.domain.notification.event.*;
import com.ssok.server.domain.notification.repository.NotificationRepository;
import com.ssok.server.domain.notification.service.*;
import com.ssok.server.domain.space.dto.InvitationCreateRequest;
import com.ssok.server.domain.space.entity.*;
import com.ssok.server.domain.space.repository.*;
import com.ssok.server.domain.space.service.*;
import com.ssok.server.domain.user.entity.User;
import com.ssok.server.domain.user.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
class NotificationIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private NotificationRepository notifications;
    @Autowired private NotificationService service;
    @Autowired private SpaceRepository spaces;
    @Autowired private SpaceMemberRepository members;
    @Autowired private UserRepository users;
    @Autowired private BookmarkRepository bookmarks;
    @Autowired private SpaceInvitationRepository invitationRepository;
    @Autowired private SpaceInvitationService invitations;
    @Autowired private SpaceMemberService membership;
    @Autowired private SpaceService spaceService;
    @Autowired private ApplicationEventPublisher events;
    @Autowired private AuthService auth;
    @Autowired private PasswordEncoder encoder;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoBean private JavaMailSender mail;
    private TransactionTemplate tx;
    private User owner, editor, viewer, recipient;
    private Long spaceId, bookmarkId, editorMemberId, viewerMemberId;

    @BeforeEach
    void setup() {
        tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            String suffix = UUID.randomUUID().toString();
            owner = user("owner", suffix); editor = user("editor", suffix);
            viewer = user("viewer", suffix); recipient = user("recipient", suffix);
            var space = spaces.save(Space.builder().name("notification team").type(SpaceType.TEAM).owner(owner).build());
            spaceId = space.getId();
            member(space, owner, SpaceRole.OWNER);
            editorMemberId = member(space, editor, SpaceRole.EDITOR).getId();
            viewerMemberId = member(space, viewer, SpaceRole.VIEWER).getId();
            bookmarkId = bookmarks.save(Bookmark.builder().space(space).title("private bookmark title").url("https://example.com").build()).getId();
        });
    }

    @AfterEach
    void cleanup() {
        if (spaces.existsById(spaceId)) {
            Long ownerId = tx.execute(status -> spaces.findById(spaceId).orElseThrow().getOwner().getId());
            spaceService.delete(ownerId, spaceId);
        }
        tx.executeWithoutResult(status -> {
            for (User user : List.of(owner, editor, viewer, recipient)) users.deleteById(user.getId());
        });
    }

    @Test
    void invitationResendDeduplicatesAndAcceptanceNotifiesCurrentOwner() {
        var invitation = invitations.create(owner.getId(), spaceId, new InvitationCreateRequest(recipient.getEmail(), null));
        assertThat(service.unreadCount(recipient.getId())).isEqualTo(1);
        assertThat(service.list(recipient.getId(), false, 0, 20).notifications().getFirst().target().requiresInvitationToken()).isTrue();
        var next = invitations.resend(owner.getId(), spaceId, invitation.invitationId());
        assertThat(service.unreadCount(recipient.getId())).isEqualTo(1);
        membership.transferOwnership(owner.getId(), spaceId, editorMemberId);
        invitations.accept(recipient.getId(), next.invitationUrl().split("#token=")[1]);
        assertThat(service.list(editor.getId(), false, 0, 20).notifications().stream().map(n -> n.type()))
                .contains(NotificationType.OWNERSHIP_TRANSFERRED, NotificationType.INVITATION_ACCEPTED);
        assertThat(service.list(owner.getId(), false, 0, 20).notifications()).isEmpty();
    }

    @Test
    void roleNoopCreatesNothingAndRemovalDisablesTargetWithoutLeakingNames() throws Exception {
        membership.updateRole(owner.getId(), spaceId, editorMemberId, SpaceRole.EDITOR);
        assertThat(service.unreadCount(editor.getId())).isZero();
        membership.updateRole(owner.getId(), spaceId, editorMemberId, SpaceRole.VIEWER);
        assertThat(service.unreadCount(editor.getId())).isEqualTo(1);
        membership.remove(owner.getId(), spaceId, editorMemberId);
        var response = service.list(editor.getId(), false, 0, 20);
        assertThat(response.notifications()).hasSize(2).allSatisfy(n -> {
            assertThat(n.target().available()).isFalse();
            assertThat(n.target().spaceId()).isNull();
            assertThat(n.message()).doesNotContain("notification team", "private bookmark title");
        });
        mvc.perform(get("/api/notifications").with(as(editor))).andExpect(status().isOk());
    }

    @Test
    void leaveNotifiesOwnerAndRollbackLeavesNoNotificationOrMembershipChange() {
        tx.executeWithoutResult(status -> {
            membership.updateRole(owner.getId(), spaceId, viewerMemberId, SpaceRole.EDITOR);
            status.setRollbackOnly();
        });
        assertThat(service.unreadCount(viewer.getId())).isZero();
        assertThat(members.findBySpaceIdAndUserId(spaceId, viewer.getId()).orElseThrow().getRole()).isEqualTo(SpaceRole.VIEWER);
        membership.leave(editor.getId(), spaceId);
        assertThat(service.list(owner.getId(), false, 0, 20).notifications().getFirst().type()).isEqualTo(NotificationType.MEMBER_LEFT);
    }

    @Test
    void readAndReadAllArePrivateIdempotentAndNewestFirst() throws Exception {
        membership.updateRole(owner.getId(), spaceId, editorMemberId, SpaceRole.VIEWER);
        membership.updateRole(owner.getId(), spaceId, editorMemberId, SpaceRole.EDITOR);
        membership.updateRole(owner.getId(), spaceId, viewerMemberId, SpaceRole.EDITOR);
        var list = service.list(editor.getId(), false, 0, 20).notifications();
        assertThat(list).hasSize(2);
        assertThat(list.getFirst().notificationId()).isGreaterThan(list.getLast().notificationId());
        Long id = list.getFirst().notificationId();
        mvc.perform(patch("/api/notifications/{id}/read", id).with(as(viewer))).andExpect(status().isNotFound());
        mvc.perform(patch("/api/notifications/{id}/read", id).with(as(editor))).andExpect(status().isOk());
        Instant readAt = notifications.findById(id).orElseThrow().getReadAt();
        mvc.perform(patch("/api/notifications/{id}/read", id).with(as(editor))).andExpect(status().isOk());
        assertThat(notifications.findById(id).orElseThrow().getReadAt()).isEqualTo(readAt);
        mvc.perform(get("/api/notifications/unread-count").with(as(editor)))
                .andExpect(jsonPath("$.data.unreadCount").value(1));
        mvc.perform(get("/api/notifications").with(as(editor)).param("unreadOnly", "true").param("size", "1"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
        mvc.perform(patch("/api/notifications/read-all").with(as(editor))).andExpect(jsonPath("$.data.updatedCount").value(1));
        mvc.perform(patch("/api/notifications/read-all").with(as(editor))).andExpect(jsonPath("$.data.updatedCount").value(0));
        assertThat(service.unreadCount(viewer.getId())).isEqualTo(1);
    }

    @Test
    void commentEventsRequireWriteExcludeAuthorAndDeduplicate() {
        tx.executeWithoutResult(status -> {
            events.publishEvent(new BookmarkCommentCreated(editor.getId(), bookmarkId, 123L));
            events.publishEvent(new BookmarkCommentCreated(editor.getId(), bookmarkId, 123L));
        });
        assertThat(service.unreadCount(editor.getId())).isZero();
        assertThat(service.unreadCount(owner.getId())).isEqualTo(1);
        assertThat(service.unreadCount(viewer.getId())).isEqualTo(1);
        assertThat(service.list(owner.getId(), false, 0, 20).notifications().getFirst().target().kind()).isEqualTo("BOOKMARK");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> tx.executeWithoutResult(status ->
                events.publishEvent(new BookmarkCommentCreated(viewer.getId(), bookmarkId, 124L)))).isInstanceOf(ForbiddenException.class);
        tx.executeWithoutResult(status -> bookmarks.deleteById(bookmarkId));
        assertThat(service.list(owner.getId(), false, 0, 20).notifications().getFirst().target().available()).isFalse();
    }

    @Test
    void expiredCancelledInvitationsAndDeletedSpaceDisableTargets() {
        var invitation = invitations.create(owner.getId(), spaceId, new InvitationCreateRequest(recipient.getEmail(), null));
        invitations.cancel(owner.getId(), spaceId, invitation.invitationId());
        assertThat(service.list(recipient.getId(), false, 0, 20).notifications().getFirst().target().available()).isFalse();
        var expired = invitations.create(owner.getId(), spaceId, new InvitationCreateRequest(recipient.getEmail(), null));
        tx.executeWithoutResult(status -> org.springframework.test.util.ReflectionTestUtils.setField(
                invitationRepository.findById(expired.invitationId()).orElseThrow(), "expiresAt", Instant.now().minusSeconds(1)));
        assertThat(service.list(recipient.getId(), false, 0, 20).notifications()).allSatisfy(n -> assertThat(n.target().available()).isFalse());
        membership.updateRole(owner.getId(), spaceId, editorMemberId, SpaceRole.VIEWER);
        spaceService.delete(owner.getId(), spaceId);
        assertThat(service.list(editor.getId(), false, 0, 20).notifications().getFirst().target().available()).isFalse();
    }

    @Test
    void returningAfterThirtyDaysGetsOneReminderAndBadLoginChangesNothing() {
        Instant previous = Instant.now().minus(40, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS);
        tx.executeWithoutResult(status -> users.findById(editor.getId()).orElseThrow().recordLogin(previous));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> auth.login(new LoginRequest(editor.getEmail(), "wrong")))
                .isInstanceOf(com.ssok.server.common.exception.InvalidCredentialsException.class);
        assertThat(users.findById(editor.getId()).orElseThrow().getLastLoginAt()).isEqualTo(previous);
        auth.login(new LoginRequest(editor.getEmail(), "test-password"));
        auth.login(new LoginRequest(editor.getEmail(), "test-password"));
        assertThat(service.list(editor.getId(), false, 0, 20).notifications()).hasSize(1)
                .allSatisfy(n -> assertThat(n.type()).isEqualTo(NotificationType.INACTIVITY_REMINDER));
        auth.login(new LoginRequest(viewer.getEmail(), "test-password"));
        assertThat(service.unreadCount(viewer.getId())).isZero();
    }

    @Test
    void duplicateConcurrentEventsProduceExactlyOneNotification() throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Void> action = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("start timed out");
                tx.executeWithoutResult(status -> events.publishEvent(new SpaceNotificationEvent(NotificationType.MEMBER_ROLE_CHANGED,
                        owner.getId(), editor.getId(), spaceId, null, "same-event")));
                return null;
            };
            var a = executor.submit(action); var b = executor.submit(action);
            try { assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); } finally { start.countDown(); }
            a.get(20, TimeUnit.SECONDS); b.get(20, TimeUnit.SECONDS);
        }
        assertThat(service.unreadCount(editor.getId())).isEqualTo(1);
        var notification = service.list(editor.getId(), false, 0, 20).notifications().getFirst();
        service.read(editor.getId(), notification.notificationId());
        Instant readAt = notifications.findById(notification.notificationId()).orElseThrow().getReadAt();
        tx.executeWithoutResult(status -> events.publishEvent(new SpaceNotificationEvent(NotificationType.MEMBER_ROLE_CHANGED,
                owner.getId(), editor.getId(), spaceId, null, "same-event")));
        assertThat(service.unreadCount(editor.getId())).isZero();
        assertThat(notifications.findById(notification.notificationId()).orElseThrow().getReadAt()).isEqualTo(readAt);
    }

    @Test
    void requiresAuthenticationAndValidPagination() throws Exception {
        mvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/notifications").with(as(owner)).param("size", "101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/notifications").with(as(owner)).param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/notifications/{id}/read", Long.MAX_VALUE).with(as(owner))).andExpect(status().isNotFound());
    }

    private User user(String name, String suffix) { return users.save(User.builder().name(name).email(name + suffix + "@notifications.test").password(encoder.encode("test-password")).build()); }
    private SpaceMember member(Space space, User user, SpaceRole role) { return members.save(SpaceMember.builder().space(space).user(user).role(role).build()); }
    private RequestPostProcessor as(User user) { return authentication(new UsernamePasswordAuthenticationToken(user.getId(), null, List.of())); }
}
