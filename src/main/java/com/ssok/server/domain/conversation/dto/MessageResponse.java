package com.ssok.server.domain.conversation.dto;

import com.ssok.server.domain.conversation.entity.MessageRole;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record MessageResponse(

        @Schema(description = "메시지 ID", example = "101")
        Long messageId,

        @Schema(description = "작성 주체", example = "ASSISTANT")
        MessageRole role,

        @Schema(description = "내용", example = "이 글을 찾으시나요?")
        String content,

        @Schema(description = "AI가 되물은 메시지인지 (질문이 모호한 경우)", example = "false")
        boolean clarification,

        @Schema(description = "링크 카드 (현재 볼 수 있는 북마크만, 유사도순)")
        List<BookmarkCardDto> cards,

        @Schema(description = "작성 시각", example = "2026-11-23T15:20:00")
        String createdAt
) {
}
