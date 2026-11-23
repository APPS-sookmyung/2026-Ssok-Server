package com.ssok.server.domain.bookmark.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AiAnalysisResultRequest(

        @Schema(description = "북마크 ID", example = "123")
        @NotNull Long bookmarkId,

        @Schema(description = "분석 요청 시 Spring이 보낸 요청 ID. 최신 요청의 결과만 반영된다.",
                example = "4f1c2b0e-8a1d-4c55-9a77-2f0c1e6b9d3a")
        @NotBlank String analysisRequestId,

        @Schema(description = "분석 결과 상태", example = "SUCCESS")
        @NotBlank String status,

        @Schema(description = "분석 결과 (실패 시 null)")
        Analysis analysis
) {

    public static final String SUCCESS = "SUCCESS";

    public boolean isSuccess() {
        return SUCCESS.equals(status) && analysis != null;
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Analysis(

            @Schema(description = "페이지 제목 (비어 있으면 기존 제목 유지)", example = "React 19 vs 18 비교")
            String title,

            @Schema(description = "요약", example = "React 19의 주요 변경점인 서버 컴포넌트와 새로운 Hook들을 대조하며...")
            String summary,

            @Schema(description = "카테고리", example = "개발/기술")
            String category,

            @Schema(description = "태그 목록", example = "[\"React\", \"React19\", \"Frontend\"]")
            List<String> tags
    ) {
    }
}
