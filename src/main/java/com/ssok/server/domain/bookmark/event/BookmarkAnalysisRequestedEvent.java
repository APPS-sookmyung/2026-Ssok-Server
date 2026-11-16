package com.ssok.server.domain.bookmark.event;

public record BookmarkAnalysisRequestedEvent(
        Long bookmarkId,
        String url
) {
}
