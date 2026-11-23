package com.ssok.server.domain.bookmark.dto;

import com.ssok.server.common.util.TimeFormatter;
import com.ssok.server.domain.bookmark.entity.Bookmark;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 같은 URL이 이미 저장된 위치. 브라우저 확장 프로그램에서 "이미 저장됨" 표시에 쓴다.
 */
public record BookmarkLookupDto(

        @Schema(description = "북마크 ID", example = "15")
        Long bookmarkId,

        @Schema(description = "제목", example = "Spring Framework")
        String title,

        @Schema(description = "스페이스 ID", example = "1")
        Long spaceId,

        @Schema(description = "스페이스 이름", example = "개인")
        String spaceName,

        @Schema(description = "저장 시각", example = "2026-11-23T15:20:00")
        String createdAt
) {

    public static BookmarkLookupDto from(Bookmark bookmark) {
        return new BookmarkLookupDto(bookmark.getId(), bookmark.getTitle(), bookmark.getSpace().getId(),
                bookmark.getSpace().getName(), TimeFormatter.format(bookmark.getCreatedAt()));
    }
}
