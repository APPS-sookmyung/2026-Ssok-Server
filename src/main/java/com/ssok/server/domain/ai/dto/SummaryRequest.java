package com.ssok.server.domain.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record SummaryRequest(

        @Schema(description = "요약할 북마크 ID", example = "15")
        @NotNull Long bookmarkId
) {
}
