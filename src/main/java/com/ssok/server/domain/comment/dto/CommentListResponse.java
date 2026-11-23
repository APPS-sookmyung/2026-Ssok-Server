package com.ssok.server.domain.comment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record CommentListResponse(

        @Schema(description = "댓글 목록 (작성 순)")
        List<CommentResponse> content,

        @Schema(description = "페이지 번호", example = "0")
        int page,

        @Schema(description = "페이지 크기", example = "20")
        int size,

        @Schema(description = "전체 댓글 수", example = "3")
        long totalElements,

        @Schema(description = "전체 페이지 수", example = "1")
        int totalPages
) {
}
