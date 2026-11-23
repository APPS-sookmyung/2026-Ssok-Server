package com.ssok.server.domain.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * "검색결과 N건"의 N은 count(권한 확인을 거쳐 실제로 보여주는 결과 수)를 쓴다.
 */
public record SemanticSearchResponse(

        @Schema(description = "결과 수 (화면의 '검색결과 N건')", example = "3")
        int count,

        @Schema(description = "검색 결과 (유사도순)")
        List<SimilarBookmarkResponse> results
) {

    public static SemanticSearchResponse of(List<SimilarBookmarkResponse> results) {
        return new SemanticSearchResponse(results.size(), results);
    }
}
