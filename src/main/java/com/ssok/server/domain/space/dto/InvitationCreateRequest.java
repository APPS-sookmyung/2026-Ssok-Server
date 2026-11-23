package com.ssok.server.domain.space.dto;

import com.ssok.server.domain.space.entity.SpaceRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record InvitationCreateRequest(@NotBlank @Email String email, SpaceRole role) {
}
