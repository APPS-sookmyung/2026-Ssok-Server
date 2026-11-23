package com.ssok.server.domain.comment.dto;

import com.ssok.server.common.util.TimeFormatter;
import com.ssok.server.domain.comment.entity.Comment;
import io.swagger.v3.oas.annotations.media.Schema;

public record CommentResponse(

        @Schema(description = "댓글 ID", example = "41")
        Long commentId,

        @Schema(description = "북마크 ID", example = "15")
        Long bookmarkId,

        @Schema(description = "작성자 (탈퇴한 사용자면 null)", nullable = true)
        CommentAuthorDto author,

        @Schema(description = "내용", example = "이 글 정리 잘 되어 있네요")
        String content,

        @Schema(description = "작성 시각", example = "2026-11-23T15:20:00")
        String createdAt,

        @Schema(description = "수정 시각 (수정한 적 없으면 null)", nullable = true)
        String editedAt,

        @Schema(description = "내가 쓴 댓글인지 (수정·삭제 버튼 표시용)", example = "true")
        boolean mine
) {

    public static CommentResponse of(Comment comment, Long viewerId) {
        return new CommentResponse(
                comment.getId(),
                comment.getBookmark().getId(),
                CommentAuthorDto.from(comment.getAuthor()),
                comment.getContent(),
                TimeFormatter.format(comment.getCreatedAt()),
                comment.getEditedAt() != null ? TimeFormatter.format(comment.getEditedAt()) : null,
                comment.isWrittenBy(viewerId));
    }
}
