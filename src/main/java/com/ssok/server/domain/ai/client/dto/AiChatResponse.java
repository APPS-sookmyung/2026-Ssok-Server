package com.ssok.server.domain.ai.client.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

/**
 * @param reply               사용자에게 보여줄 답변 또는 되묻는 질문
 * @param needsClarification  질문이 모호해 되물은 경우 true (results는 비어 있을 수 있음)
 * @param results             후보 북마크 (유사도순)
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AiChatResponse(
        String reply,
        boolean needsClarification,
        List<AiSearchResponse.Result> results
) {
}
