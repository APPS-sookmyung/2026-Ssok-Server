package com.ssok.server.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record RegisterResponse(

        @Schema(description = "유저 ID", example = "1")
        Long userId,

        @Schema(description = "이메일", example = "user@test.com")
        String email
) {
}
