package com.ssok.server.domain.ai.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

/**
 * @param spaceIds  Spring이 권한을 확인한 검색 대상 스페이스. AI는 이 안에서만 검색한다.
 * @param savedFrom 이 시각 이후(포함) 저장된 북마크만. 없으면 생략
 * @param savedTo   이 시각 이전(미포함) 저장된 북마크만. 없으면 생략
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiSearchRequest(
        String query,
        int topK,
        List<Long> spaceIds,
        String savedFrom,
        String savedTo
) {

    public AiSearchRequest(String query, int topK, List<Long> spaceIds) {
        this(query, topK, spaceIds, null, null);
    }
}
