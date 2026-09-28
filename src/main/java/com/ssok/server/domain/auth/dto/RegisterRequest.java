package com.ssok.server.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RegisterRequest(

        @Schema(description = "이메일", example = "user@test.com")
        @NotBlank @Email String email,

        @Schema(description = "비밀번호", example = "pw1234!")
        @NotBlank String password,

        @Schema(description = "아이디", example = "ssok_user")
        @NotBlank String name
) {
}
