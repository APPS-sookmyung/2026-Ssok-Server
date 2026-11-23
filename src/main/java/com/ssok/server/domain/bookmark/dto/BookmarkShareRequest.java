package com.ssok.server.domain.bookmark.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record BookmarkShareRequest(

        @Schema(description = "공유할 북마크 ID 목록 (최대 100개)", example = "[15, 16]")
        @NotEmpty(message = "bookmarkIds is required")
        @Size(max = 100, message = "up to 100 bookmarks at once")
        List<@NotNull Long> bookmarkIds,

        @Schema(description = "공유받을 스페이스 ID", example = "4")
        @NotNull(message = "targetSpaceId is required")
        Long targetSpaceId
) {
}
