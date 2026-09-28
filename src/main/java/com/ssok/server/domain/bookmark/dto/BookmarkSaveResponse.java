package com.ssok.server.domain.bookmark.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record BookmarkSaveResponse(

        @Schema(description = "북마크 ID", example = "15")
        Long bookmarkId,

        @Schema(description = "제목", example = "Spring Framework")
        String title,

        @Schema(description = "요약", example = "Java 기반 웹 프레임워크")
        String summary,

        @Schema(description = "태그 목록")
        List<String> tags,

        @Schema(description = "생성 시각", example = "2026-06-30T14:30:00")
        String createdAt
) {
}
