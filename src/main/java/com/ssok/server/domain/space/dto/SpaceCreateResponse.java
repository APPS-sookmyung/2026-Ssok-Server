package com.ssok.server.domain.space.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record SpaceCreateResponse(

        @Schema(description = "스페이스 ID", example = "1")
        Long spaceId,

        @Schema(description = "스페이스명", example = "캡스톤 프로젝트")
        String name,

        @Schema(description = "스페이스 설명", example = "팀 프로젝트 자료를 관리하는 공간")
        String description,

        @Schema(description = "스페이스 유형", example = "TEAM")
        String type
) {
}
