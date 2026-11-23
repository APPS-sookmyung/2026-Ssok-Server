package com.ssok.server.domain.conversation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ConversationListResponse(

        @Schema(description = "대화 목록 (최근 대화순)")
        List<ConversationSummaryDto> content,

        @Schema(description = "페이지 번호", example = "0")
        int page,

        @Schema(description = "페이지 크기", example = "20")
        int size,

        @Schema(description = "전체 개수", example = "7")
        long totalElements,

        @Schema(description = "전체 페이지 수", example = "1")
        int totalPages
) {
}
