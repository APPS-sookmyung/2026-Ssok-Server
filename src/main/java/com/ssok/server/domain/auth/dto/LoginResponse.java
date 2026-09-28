package com.ssok.server.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record LoginResponse(

        @Schema(description = "액세스 토큰")
        String accessToken,

        @Schema(description = "리프레시 토큰")
        String refreshToken,

        @Schema(description = "유저 ID", example = "1")
        Long userId,

        @Schema(description = "닉네임", example = "홍예빈")
        String nickname
) {
}
