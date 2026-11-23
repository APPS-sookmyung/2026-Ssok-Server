package com.ssok.server.domain.ai.client.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

/**
 * @param spaceIds Spring이 권한을 확인한 검색 대상 스페이스. AI는 이 안에서만 검색한다.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AiSearchRequest(
        String query,
        int topK,
        List<Long> spaceIds
) {
}
