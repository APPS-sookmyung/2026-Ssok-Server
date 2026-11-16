package com.ssok.server.domain.space.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record MemberInviteResponse(

        @Schema(description = "팀원 ID", example = "5")
        Long memberId,

        @Schema(description = "이메일", example = "member@test.com")
        String email
) {
}
