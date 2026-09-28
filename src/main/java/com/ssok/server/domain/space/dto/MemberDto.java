package com.ssok.server.domain.space.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record MemberDto(

        @Schema(description = "팀원 ID", example = "1")
        Long memberId,

        @Schema(description = "유저 ID", example = "1")
        Long userId,

        @Schema(description = "닉네임", example = "홍예빈")
        String nickname,

        @Schema(description = "이메일", example = "yebin@test.com")
        String email,

        @Schema(description = "역할", example = "OWNER")
        String role
) {
}
