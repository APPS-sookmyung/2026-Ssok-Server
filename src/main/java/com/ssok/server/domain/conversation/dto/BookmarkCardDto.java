package com.ssok.server.domain.conversation.dto;

import com.ssok.server.domain.bookmark.entity.Bookmark;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 대화 응답에 붙는 북마크 링크 카드.
 */
public record BookmarkCardDto(

        @Schema(description = "북마크 ID", example = "15")
        Long bookmarkId,

        @Schema(description = "제목", example = "React 19 vs 18 비교")
        String title,

        @Schema(description = "URL", example = "https://example.com/react-19-vs-18")
        String url,

        @Schema(description = "요약", nullable = true)
        String summary,

        @Schema(description = "스페이스 ID", example = "3")
        Long spaceId,

        @Schema(description = "스페이스 이름", example = "프론트엔드 스터디")
        String spaceName
) {

    public static BookmarkCardDto from(Bookmark bookmark) {
        return new BookmarkCardDto(bookmark.getId(), bookmark.getTitle(), bookmark.getUrl(), bookmark.getSummary(),
                bookmark.getSpace().getId(), bookmark.getSpace().getName());
    }
}
