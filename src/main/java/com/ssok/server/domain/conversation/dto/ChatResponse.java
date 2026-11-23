package com.ssok.server.domain.conversation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record ChatResponse(

        @Schema(description = "대화 ID (새 대화면 새로 생성된 ID)", example = "12")
        Long conversationId,

        @Schema(description = "대화 제목", example = "지난주에 저장한 리액트 버전 비교 글 찾아줘")
        String title,

        @Schema(description = "방금 보낸 질문")
        MessageResponse question,

        @Schema(description = "AI 응답")
        MessageResponse answer
) {
}
