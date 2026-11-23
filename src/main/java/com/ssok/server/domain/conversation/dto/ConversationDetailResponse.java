package com.ssok.server.domain.conversation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ConversationDetailResponse(

        @Schema(description = "대화 ID", example = "12")
        Long conversationId,

        @Schema(description = "제목", example = "지난주에 저장한 리액트 버전 비교 글 찾아줘")
        String title,

        @Schema(description = "대화 시작 시각", example = "2026-11-23T15:19:00")
        String createdAt,

        @Schema(description = "메시지 (오래된 순). 링크 카드는 지금 볼 수 있는 북마크만 포함")
        List<MessageResponse> messages
) {
}
