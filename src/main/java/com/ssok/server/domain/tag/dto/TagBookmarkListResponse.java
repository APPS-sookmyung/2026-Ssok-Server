package com.ssok.server.domain.tag.dto;

import com.ssok.server.domain.bookmark.dto.BookmarkSummaryDto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record TagBookmarkListResponse(

        @Schema(description = "태그명", example = "Java")
        String tagName,

        @Schema(description = "북마크 목록")
        List<BookmarkSummaryDto> content,

        @Schema(description = "페이지 번호", example = "0")
        int page,

        @Schema(description = "페이지 크기", example = "20")
        int size,

        @Schema(description = "전체 개수", example = "24")
        long totalElements,

        @Schema(description = "전체 페이지 수", example = "2")
        int totalPages
) {
}
