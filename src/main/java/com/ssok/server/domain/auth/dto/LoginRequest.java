package com.ssok.server.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(

        @Schema(description = "이메일", example = "user@test.com")
        @NotBlank(message = "email and password are required") String email,

        @Schema(description = "비밀번호", example = "pw1234!")
        @NotBlank(message = "email and password are required") String password
) {
}
