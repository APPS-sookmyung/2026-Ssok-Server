package com.ssok.server.domain.space.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record SpaceCreateRequest(

        @Schema(description = "스페이스명", example = "캡스톤 프로젝트")
        @NotBlank(message = "invalid request") String spaceName,

        @Schema(description = "스페이스 유형(개인/팀)", example = "TEAM")
        @NotBlank(message = "invalid request") String type
) {
}
