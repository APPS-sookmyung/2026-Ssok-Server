package com.ssok.server.domain.space.dto;

import com.ssok.server.domain.space.entity.SpaceRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record MemberRoleUpdateRequest(
        @NotNull @Schema(description = "변경할 역할 (EDITOR 또는 VIEWER)", example = "VIEWER") SpaceRole role
) {
}
