package com.ssok.server.domain.bookmark.dto;

import com.ssok.server.domain.bookmark.entity.AnalysisStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record BookmarkSummaryDto(

        @Schema(description = "북마크 ID", example = "15")
        Long bookmarkId,

        @Schema(description = "제목", example = "Spring Framework")
        String title,

        @Schema(description = "요약", example = "Java 기반 웹 프레임워크")
        String summary,

        @Schema(description = "방문 횟수", example = "12")
        int visitCount,

        @Schema(description = "마지막 방문 시각", example = "2026-06-29T21:10:33")
        String lastVisitedAt,

        @Schema(description = "카테고리", example = "개발/기술")
        String category,

        @Schema(description = "AI 분석 상태", example = "SUCCESS")
        AnalysisStatus analysisStatus,

        @Schema(description = "태그 목록")
        List<String> tags,

        @Schema(description = "라이브러리 ID. 미분류는 null") Long libraryId,
        com.ssok.server.domain.bookmark.entity.BookmarkType type,
        Long fileId
) {
    public static BookmarkSummaryDto from(com.ssok.server.domain.bookmark.entity.Bookmark bookmark) {
        return new BookmarkSummaryDto(bookmark.getId(), bookmark.getTitle(), bookmark.getSummary(), bookmark.getVisitCount(),
                bookmark.getLastVisitedAt() == null ? null : com.ssok.server.common.util.TimeFormatter.format(bookmark.getLastVisitedAt()),
                bookmark.getCategory(), bookmark.getAnalysisStatus(), bookmark.getTags(),
                bookmark.getLibrary() == null ? null : bookmark.getLibrary().getId(),
                bookmark.getType(), bookmark.getFile() == null ? null : bookmark.getFile().getId());
    }
}
