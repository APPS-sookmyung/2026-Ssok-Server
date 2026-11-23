package com.ssok.server.domain.bookmark.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record BookmarkBulkResponse(

        @Schema(description = "처리된 북마크 ID 목록", example = "[15, 16]")
        List<Long> bookmarkIds,

        @Schema(description = "처리된 개수", example = "2")
        int count
) {

    public static BookmarkBulkResponse of(List<Long> bookmarkIds) {
        return new BookmarkBulkResponse(bookmarkIds, bookmarkIds.size());
    }
}
