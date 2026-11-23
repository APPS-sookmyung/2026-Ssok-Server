package com.ssok.server.domain.comment.event;

/**
 * 댓글이 작성됐을 때 발행한다. 알림 기능에서 구독해 북마크 저장자·스페이스 멤버에게 알린다.
 *
 * @param bookmarkSaverId 북마크를 저장한 사용자 (알 수 없으면 null)
 */
public record CommentCreatedEvent(
        Long commentId,
        Long bookmarkId,
        Long spaceId,
        Long authorId,
        Long bookmarkSaverId
) {
}
