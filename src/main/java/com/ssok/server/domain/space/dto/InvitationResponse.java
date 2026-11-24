package com.ssok.server.domain.space.dto;

import com.ssok.server.domain.space.entity.SpaceInvitation;
import java.time.Instant;

public record InvitationResponse(Long invitationId, Long spaceId, String email, String role,
        String status, String delivery, Instant expiresAt, String invitationUrl) {
    public static InvitationResponse from(SpaceInvitation invitation, String url) {
        String status = invitation.getStatus().name();
        if (invitation.getStatus() == SpaceInvitation.Status.PENDING && !invitation.getExpiresAt().isAfter(Instant.now())) {
            status = "EXPIRED";
        }
        return new InvitationResponse(invitation.getId(), invitation.getSpace().getId(), invitation.getEmail(),
                invitation.getRole().name(), status, invitation.getDelivery().name(), invitation.getExpiresAt(), url);
    }
}
