package com.ssok.server.domain.bookmark.event;

import com.ssok.server.domain.bookmark.entity.Bookmark;
import java.time.LocalDateTime;

/**
 * @param savedAt 북마크 최초 저장 시각. 재분석 시에도 바뀌지 않는다.
 */
public record BookmarkAnalysisRequestedEvent(
        Long bookmarkId,
        String url,
        Long spaceId,
        LocalDateTime savedAt,
        String analysisRequestId
) {

    public static BookmarkAnalysisRequestedEvent from(Bookmark bookmark) {
        return new BookmarkAnalysisRequestedEvent(bookmark.getId(), bookmark.getUrl(), bookmark.getSpace().getId(),
                bookmark.getCreatedAt(), bookmark.getAnalysisRequestId());
    }
}
