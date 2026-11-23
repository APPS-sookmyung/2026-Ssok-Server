package com.ssok.server.domain.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public record SemanticSearchRequest(

        @Schema(description = "검색어(자연어)", example = "자바 웹 프레임워크")
        @NotBlank(message = "search query is required") String query,

        @Schema(description = "검색할 스페이스 ID. 생략하면 내가 속한 모든 스페이스(개인 + 소속 팀)에서 검색",
                example = "3", nullable = true)
        Long spaceId,

        @Schema(description = "이 날짜부터(포함) 저장된 사이트만", example = "2026-11-01", nullable = true)
        LocalDate savedFrom,

        @Schema(description = "이 날짜까지(포함) 저장된 사이트만", example = "2026-11-23", nullable = true)
        LocalDate savedTo,

        @Schema(description = "최대 결과 수 (1~50, 기본 10)", example = "10", nullable = true)
        @Min(value = 1, message = "limit must be between 1 and 50")
        @Max(value = 50, message = "limit must be between 1 and 50")
        Integer limit
) {

    public static final int DEFAULT_LIMIT = 10;

    public SemanticSearchRequest(String query) {
        this(query, null, null, null, null);
    }

    public SemanticSearchRequest(String query, Long spaceId) {
        this(query, spaceId, null, null, null);
    }

    public int limitOrDefault() {
        return limit != null ? limit : DEFAULT_LIMIT;
    }
}
