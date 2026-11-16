package com.ssok.server.domain.ai.client.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AiSearchResponse(
        String query,
        List<Result> results
) {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Result(
            Long bookmarkId,
            double similarity
    ) {
    }
}
