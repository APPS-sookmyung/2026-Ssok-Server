package com.ssok.server.domain.comment.dto;

import com.ssok.server.common.util.TimeFormatter;
import com.ssok.server.domain.comment.entity.Comment;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 북마크 카드에 보여줄 최근 댓글 미리보기.
 */
public record CommentPreviewDto(

        @Schema(description = "작성자 이름 (탈퇴한 사용자면 null)", example = "홍길동", nullable = true)
        String authorName,

        @Schema(description = "내용 (최대 50자, 넘으면 말줄임)", example = "이 글 정리 잘 되어 있네요")
        String content,

        @Schema(description = "작성 시각", example = "2026-11-23T15:20:00")
        String createdAt
) {

    private static final int PREVIEW_LENGTH = 50;

    public static CommentPreviewDto from(Comment comment) {
        String content = comment.getContent();
        if (content.length() > PREVIEW_LENGTH) {
            content = content.substring(0, PREVIEW_LENGTH) + "…";
        }
        return new CommentPreviewDto(
                comment.getAuthor() != null ? comment.getAuthor().getName() : null,
                content,
                TimeFormatter.format(comment.getCreatedAt()));
    }
}
