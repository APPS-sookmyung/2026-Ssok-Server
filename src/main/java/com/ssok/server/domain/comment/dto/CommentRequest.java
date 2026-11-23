package com.ssok.server.domain.comment.dto;

import com.ssok.server.domain.comment.entity.Comment;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommentRequest(

        @Schema(description = "댓글 내용 (최대 1000자)", example = "이 글 정리 잘 되어 있네요")
        @NotBlank(message = "content is required")
        @Size(max = Comment.MAX_CONTENT_LENGTH, message = "content must be 1000 characters or less")
        String content
) {
}
