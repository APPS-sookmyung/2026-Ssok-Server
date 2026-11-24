package com.ssok.server.domain.space.service;

import com.ssok.server.domain.space.entity.SpaceInvitation;
import com.ssok.server.domain.space.repository.SpaceInvitationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
public class InvitationMailDelivery {
    private final ObjectProvider<JavaMailSender> sender;
    private final SpaceInvitationRepository invitations;
    private final String from;

    public InvitationMailDelivery(ObjectProvider<JavaMailSender> sender, SpaceInvitationRepository invitations,
            @Value("${invitation.mail-from:}") String from) {
        this.sender = sender;
        this.invitations = invitations;
        this.from = from;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void deliver(InvitationMailRequested event) {
        SpaceInvitation.Delivery result = SpaceInvitation.Delivery.FAILED;
        try {
            JavaMailSender mailSender = sender.getIfAvailable();
            if (mailSender != null && !from.isBlank()) {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(from);
                message.setTo(event.email());
                message.setSubject("SSok 팀 스페이스 초대");
                message.setText("초대받은 이메일로 로그인한 뒤 아래 링크에서 수락해 주세요.\n" + event.url());
                mailSender.send(message);
                result = SpaceInvitation.Delivery.SENT;
            }
        } catch (RuntimeException e) {
            // SMTP 예외 본문에 주소·토큰이 포함될 수 있으므로 식별자만 기록한다.
            log.warn("Invitation mail delivery failed: invitationId={}", event.invitationId());
        }
        try {
            invitations.recordDelivery(event.invitationId(), event.tokenHash(), result);
        } catch (RuntimeException e) {
            log.warn("Invitation delivery status update failed: invitationId={}", event.invitationId());
        }
    }
}
