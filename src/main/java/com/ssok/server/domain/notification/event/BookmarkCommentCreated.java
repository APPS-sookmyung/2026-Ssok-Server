package com.ssok.server.domain.notification.event;

/** 댓글 저장 후 같은 DB 트랜잭션에서 발행. 클라이언트용 이벤트 생성 API는 제공하지 않는다. */
public record BookmarkCommentCreated(Long actorId, Long bookmarkId, Long commentId) {
}
