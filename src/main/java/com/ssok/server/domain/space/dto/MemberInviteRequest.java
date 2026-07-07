package com.ssok.server.domain.space.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record MemberInviteRequest(

        @Schema(description = "초대할 사용자 이메일", example = "member@test.com")
        @NotBlank @Email String email
) {
}
