package com.ssok.server.domain.ai.dto;

import com.ssok.server.domain.bookmark.entity.AnalysisStatus;
import io.swagger.v3.oas.annotations.media.Schema;

public record SummaryResponse(

        @Schema(description = "북마크 ID", example = "15")
        Long bookmarkId,

        @Schema(description = "AI 분석 상태", example = "PENDING")
        AnalysisStatus analysisStatus
) {
}
