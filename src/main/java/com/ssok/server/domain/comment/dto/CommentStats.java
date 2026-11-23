package com.ssok.server.domain.comment.dto;

/**
 * 북마크 카드용 댓글 개수와 최근 댓글.
 */
public record CommentStats(long count, CommentPreviewDto latest) {

    public static final CommentStats EMPTY = new CommentStats(0, null);
}
