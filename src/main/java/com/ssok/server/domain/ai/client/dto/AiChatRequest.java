package com.ssok.server.domain.ai.client.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

/**
 * 대화 검색 요청.
 *
 * @param messages 최근 메시지 최대 10개 (오래된 것부터, 마지막이 이번 사용자 질문)
 * @param spaceIds Spring이 권한을 확인한 검색 대상 스페이스
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AiChatRequest(
        List<Message> messages,
        List<Long> spaceIds,
        int topK
) {

    /**
     * @param role user 또는 assistant
     */
    public record Message(String role, String content) {
    }
}
