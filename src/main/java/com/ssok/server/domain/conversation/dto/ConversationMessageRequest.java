package com.ssok.server.domain.conversation.dto;

import com.ssok.server.domain.conversation.entity.ConversationMessage;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConversationMessageRequest(

        @Schema(description = "질문 (최대 2000자)", example = "지난주에 저장한 리액트 버전 비교 글 찾아줘")
        @NotBlank(message = "message is required")
        @Size(max = ConversationMessage.MAX_CONTENT_LENGTH, message = "message must be 2000 characters or less")
        String message,

        @Schema(description = "검색할 스페이스 ID. 생략하면 내가 속한 모든 스페이스(개인 + 소속 팀)에서 검색",
                example = "3", nullable = true)
        Long spaceId
) {
}
