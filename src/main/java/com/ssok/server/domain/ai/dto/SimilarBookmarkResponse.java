package com.ssok.server.domain.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record SimilarBookmarkResponse(

        @Schema(description = "북마크 ID", example = "28")
        Long bookmarkId,

        @Schema(description = "제목", example = "Spring Boot Guide")
        String title,

        @Schema(description = "유사도", example = "0.94")
        double similarity
) {
}
