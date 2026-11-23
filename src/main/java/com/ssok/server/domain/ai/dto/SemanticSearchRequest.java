package com.ssok.server.domain.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record SemanticSearchRequest(

        @Schema(description = "검색어(자연어)", example = "자바 웹 프레임워크")
        @NotBlank(message = "search query is required") String query,

        @Schema(description = "검색할 스페이스 ID. 생략하면 내가 속한 모든 스페이스(개인 + 소속 팀)에서 검색",
                example = "3", nullable = true)
        Long spaceId
) {

    public SemanticSearchRequest(String query) {
        this(query, null);
    }
}
