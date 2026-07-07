package com.ssok.server.domain.space.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record SpaceSummaryDto(

        @Schema(description = "스페이스 ID", example = "1")
        Long spaceId,

        @Schema(description = "스페이스명", example = "개인")
        String name,

        @Schema(description = "스페이스 유형", example = "PERSONAL")
        String type,

        @Schema(description = "북마크 수", example = "35")
        long bookmarkCount
) {
}
