package com.ssok.server.domain.bookmark.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record BookmarkShareResponse(

        @Schema(description = "새로 만들어진 북마크")
        List<Shared> shared,

        @Schema(description = "대상 스페이스에 같은 URL이 이미 있어 건너뛴 원본 북마크 ID", example = "[17]")
        List<Long> skippedBookmarkIds
) {

    public record Shared(

            @Schema(description = "원본 북마크 ID", example = "15")
            Long sourceBookmarkId,

            @Schema(description = "대상 스페이스에 만들어진 북마크 ID", example = "52")
            Long bookmarkId
    ) {
    }
}
