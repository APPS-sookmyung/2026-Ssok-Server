package com.ssok.server.domain.conversation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record ConversationSummaryDto(

        @Schema(description = "대화 ID", example = "12")
        Long conversationId,

        @Schema(description = "제목 (첫 질문)", example = "지난주에 저장한 리액트 버전 비교 글 찾아줘")
        String title,

        @Schema(description = "마지막 메시지 미리보기 (최대 80자)", example = "이 글을 찾으시나요?")
        String lastMessagePreview,

        @Schema(description = "마지막 메시지 시각", example = "2026-11-23T15:20:00")
        String lastMessageAt,

        @Schema(description = "대화 시작 시각", example = "2026-11-23T15:19:00")
        String createdAt
) {
}
