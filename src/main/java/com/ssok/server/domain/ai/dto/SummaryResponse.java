package com.ssok.server.domain.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record SummaryResponse(

        @Schema(description = "북마크 ID", example = "15")
        Long bookmarkId,

        @Schema(description = "요약", example = "Spring Framework는 Java 기반의 오픈소스 애플리케이션 프레임워크로...")
        String summary
) {
}
