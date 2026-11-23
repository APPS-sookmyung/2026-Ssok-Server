package com.ssok.server.domain.bookmark.dto;

import com.ssok.server.domain.bookmark.entity.AnalysisStatus;
import com.ssok.server.domain.bookmark.entity.Bookmark;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 분석 상태 확인용. 완료되면 카드에 바로 반영할 수 있도록 분석 결과도 함께 준다.
 */
public record AnalysisStatusDto(

        @Schema(description = "북마크 ID", example = "15")
        Long bookmarkId,

        @Schema(description = "분석 상태 (PENDING: 처리 중, SUCCESS: 완료, FAILED: 실패)", example = "SUCCESS")
        AnalysisStatus analysisStatus,

        @Schema(description = "제목", example = "React 19 vs 18 비교")
        String title,

        @Schema(description = "요약 (완료 전에는 null)", nullable = true)
        String summary,

        @Schema(description = "카테고리 (완료 전에는 null)", nullable = true)
        String category,

        @Schema(description = "태그 목록")
        List<String> tags
) {

    public static AnalysisStatusDto from(Bookmark bookmark) {
        return new AnalysisStatusDto(bookmark.getId(), bookmark.getAnalysisStatus(), bookmark.getTitle(),
                bookmark.getSummary(), bookmark.getCategory(), List.copyOf(bookmark.getTags()));
    }
}
