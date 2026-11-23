package com.ssok.server.domain.space.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.ssok.server.common.exception.InvitationConflictException;
import com.ssok.server.domain.space.dto.InvitationCreateRequest;
import com.ssok.server.domain.space.entity.*;
import com.ssok.server.domain.space.repository.*;
import com.ssok.server.domain.user.entity.User;
import com.ssok.server.domain.user.repository.UserRepository;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = "invitation.mail-from=test@ssok.invalid")
class InvitationDeliveryConcurrencyTest {
    @Autowired private SpaceRepository spaces;
    @Autowired private SpaceMemberRepository members;
    @Autowired private SpaceInvitationRepository invitations;
    @Autowired private UserRepository users;
    @Autowired private SpaceInvitationService service;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoBean private JavaMailSender sender;

    @Test
    void committedInvitationSendsMailAndConcurrentAcceptanceCreatesOneMembership() throws Exception {
        var tx = new TransactionTemplate(transactionManager);
        Long[] ids = fixture(tx);
        try {
            String email = users.findById(ids[2]).orElseThrow().getEmail();
            var invitation = service.create(ids[1], ids[0], new InvitationCreateRequest(email, SpaceRole.EDITOR));
            verify(sender).send(any(SimpleMailMessage.class));
            assertThat(invitations.findById(invitation.invitationId()).orElseThrow().getDelivery()).isEqualTo(SpaceInvitation.Delivery.SENT);
            String token = invitation.invitationUrl().split("#token=")[1];
            var ready = new CountDownLatch(2);
            var start = new CountDownLatch(1);
            try (var executor = Executors.newFixedThreadPool(2)) {
                var a = executor.submit(() -> accept(ids[2], token, ready, start));
                var b = executor.submit(() -> accept(ids[2], token, ready, start));
                try { assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); }
                finally { start.countDown(); }
                assertThat((a.get(20, TimeUnit.SECONDS) ? 1 : 0) + (b.get(20, TimeUnit.SECONDS) ? 1 : 0)).isEqualTo(1);
            }
            assertThat(members.countBySpaceId(ids[0])).isEqualTo(2);
            assertThat(invitations.findById(invitation.invitationId()).orElseThrow().getStatus()).isEqualTo(SpaceInvitation.Status.ACCEPTED);
        } finally { cleanup(tx, ids); }
    }

    @Test
    void mailFailurePersistsAndOwnerCanReissueAndRolledBackInvitationSendsNothing() {
        var tx = new TransactionTemplate(transactionManager);
        Long[] ids = fixture(tx);
        try {
            String email = users.findById(ids[2]).orElseThrow().getEmail();
            doThrow(new MailSendException("test failure")).when(sender).send(any(SimpleMailMessage.class));
            var issued = service.create(ids[1], ids[0], new InvitationCreateRequest(email, null));
            assertThat(invitations.findById(issued.invitationId()).orElseThrow().getDelivery()).isEqualTo(SpaceInvitation.Delivery.FAILED);
            reset(sender);
            var reissued = service.resend(ids[1], ids[0], issued.invitationId());
            assertThat(reissued.invitationUrl()).isNotEqualTo(issued.invitationUrl());
            assertThat(invitations.findById(issued.invitationId()).orElseThrow().getDelivery()).isEqualTo(SpaceInvitation.Delivery.SENT);
            clearInvocations(sender);
            tx.executeWithoutResult(status -> {
                service.create(ids[1], ids[0], new InvitationCreateRequest("rollback@ssok.invalid", null));
                status.setRollbackOnly();
            });
            verifyNoInteractions(sender);
            assertThat(invitations.findAllBySpaceIdOrderByIdDesc(ids[0])).hasSize(1);
        } finally { cleanup(tx, ids); }
    }

    private boolean accept(Long userId, String token, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("accept start timed out");
        try { service.accept(userId, token); return true; }
        catch (InvitationConflictException expected) { return false; }
    }

    private Long[] fixture(TransactionTemplate tx) {
        return tx.execute(status -> {
            String suffix = UUID.randomUUID().toString();
            User owner = users.save(User.builder().name("owner").email("owner" + suffix + "@ssok.invalid").password("test").build());
            User recipient = users.save(User.builder().name("recipient").email("recipient" + suffix + "@ssok.invalid").password("test").build());
            Space space = spaces.save(Space.builder().name("invitation concurrency").type(SpaceType.TEAM).owner(owner).build());
            members.save(SpaceMember.builder().space(space).user(owner).role(SpaceRole.OWNER).build());
            return new Long[]{space.getId(), owner.getId(), recipient.getId()};
        });
    }

    private void cleanup(TransactionTemplate tx, Long[] ids) {
        tx.executeWithoutResult(status -> {
            spaces.deleteById(ids[0]); spaces.flush();
            users.deleteById(ids[1]); users.deleteById(ids[2]);
        });
    }
}
