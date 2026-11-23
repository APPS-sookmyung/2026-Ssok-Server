package com.ssok.server.domain.space.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InvitationAcceptRequest(@NotBlank @Size(max = 128) String token) {
}
