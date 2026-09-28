package com.ssok.server.domain.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record SemanticSearchRequest(

        @Schema(description = "검색어(자연어)", example = "자바 웹 프레임워크")
        @NotBlank(message = "search query is required") String query
) {
}
