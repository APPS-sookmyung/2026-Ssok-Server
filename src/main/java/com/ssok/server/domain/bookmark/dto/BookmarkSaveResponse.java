package com.ssok.server.domain.bookmark.dto;

import com.ssok.server.domain.bookmark.entity.AnalysisStatus;
import io.swagger.v3.oas.annotations.media.Schema;

public record BookmarkSaveResponse(

        @Schema(description = "북마크 ID", example = "15")
        Long bookmarkId,

        @Schema(description = "제목", example = "spring.io")
        String title,

        @Schema(description = "AI 분석 상태", example = "PENDING")
        AnalysisStatus analysisStatus,

        @Schema(description = "생성 시각", example = "2026-06-30T14:30:00")
        String createdAt
) {
}
