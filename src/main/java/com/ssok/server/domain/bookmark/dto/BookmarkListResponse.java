package com.ssok.server.domain.bookmark.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record BookmarkListResponse(

        @Schema(description = "북마크 목록")
        List<BookmarkSummaryDto> content,

        @Schema(description = "페이지 번호", example = "0")
        int page,

        @Schema(description = "페이지 크기", example = "20")
        int size,

        @Schema(description = "전체 개수", example = "52")
        long totalElements,

        @Schema(description = "전체 페이지 수", example = "3")
        int totalPages
) {
}
