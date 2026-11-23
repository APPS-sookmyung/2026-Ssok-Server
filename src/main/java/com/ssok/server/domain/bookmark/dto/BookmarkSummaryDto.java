package com.ssok.server.domain.bookmark.dto;

import com.ssok.server.common.util.TimeFormatter;
import com.ssok.server.domain.bookmark.entity.AnalysisStatus;
import com.ssok.server.domain.bookmark.entity.Bookmark;
import com.ssok.server.domain.comment.dto.CommentPreviewDto;
import com.ssok.server.domain.comment.dto.CommentStats;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

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
        String createdAt,

        @Schema(description = "댓글 수", example = "3")
        long commentCount,

        @Schema(description = "가장 최근 댓글 미리보기 (댓글이 없으면 null)", nullable = true)
        CommentPreviewDto latestComment
) {

    /**
     * 댓글 정보는 여러 북마크를 한꺼번에 조회해 넘겨받는다(statsByBookmarkId).
     */
    public static Function<Bookmark, BookmarkSummaryDto> withComments(Map<Long, CommentStats> statsByBookmarkId) {
        return bookmark -> from(bookmark, statsByBookmarkId.getOrDefault(bookmark.getId(), CommentStats.EMPTY));
    }

    public static BookmarkSummaryDto from(Bookmark bookmark, CommentStats comments) {
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
                TimeFormatter.format(bookmark.getCreatedAt()),
                comments.count(),
                comments.latest());
    }
}
