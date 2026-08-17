package com.ssok.server.domain.space.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record SpaceDetailResponse(

        @Schema(description = "스페이스 ID", example = "2")
        Long spaceId,

        @Schema(description = "스페이스명", example = "캡스톤 프로젝트")
        String name,

        @Schema(description = "설명", example = "캡스톤 프로젝트 자료를 관리하는 스페이스")
        String description,

        @Schema(description = "스페이스 유형", example = "TEAM")
        String type,

        @Schema(description = "소유자 ID", example = "1")
        Long ownerId,

        @Schema(description = "멤버 수", example = "4")
        long memberCount,

        @Schema(description = "북마크 수", example = "82")
        long bookmarkCount,

        @Schema(description = "생성 시각", example = "2026-06-15T10:20:35")
        String createdAt
) {
}
