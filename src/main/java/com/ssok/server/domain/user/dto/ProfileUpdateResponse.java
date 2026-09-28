package com.ssok.server.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record ProfileUpdateResponse(

        @Schema(description = "유저 ID", example = "1")
        Long userId,

        @Schema(description = "닉네임", example = "홍예빈")
        String nickname,

        @Schema(description = "수정 시각", example = "2026-06-30T18:20:31")
        String updatedAt
) {
}
