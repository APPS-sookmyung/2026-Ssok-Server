package com.ssok.server.domain.bookmark.dto;

import com.ssok.server.common.util.TimeFormatter;
import com.ssok.server.domain.bookmark.entity.AnalysisStatus;
import com.ssok.server.domain.bookmark.entity.Bookmark;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record BookmarkSummaryDto(

        @Schema(description = "북마크 ID", example = "15")
        Long bookmarkId,

        @Schema(description = "제목", example = "Spring Framework")
        String title,

        @Schema(description = "URL", example = "https://spring.io")
        String url,

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

        @Schema(description = "스페이스 ID", example = "1")
        Long spaceId,

        @Schema(description = "스페이스 이름", example = "프론트엔드 스터디")
        String spaceName,

        @Schema(description = "저장한 사용자 (알 수 없으면 null)", nullable = true)
        BookmarkSaverDto savedBy,

        @Schema(description = "저장 시각", example = "2026-06-29T21:10:33")
        String createdAt
) {

    public static BookmarkSummaryDto from(Bookmark bookmark) {
        return new BookmarkSummaryDto(
                bookmark.getId(),
                bookmark.getTitle(),
                bookmark.getUrl(),
                bookmark.getSummary(),
                bookmark.getVisitCount(),
                bookmark.getLastVisitedAt() != null ? TimeFormatter.format(bookmark.getLastVisitedAt()) : null,
                bookmark.getCategory(),
                bookmark.getAnalysisStatus(),
                List.copyOf(bookmark.getTags()),
                bookmark.getSpace().getId(),
                bookmark.getSpace().getName(),
                BookmarkSaverDto.from(bookmark.getSavedBy()),
                TimeFormatter.format(bookmark.getCreatedAt()));
    }
}
