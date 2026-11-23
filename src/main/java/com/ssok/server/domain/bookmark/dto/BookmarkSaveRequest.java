package com.ssok.server.domain.bookmark.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public record BookmarkSaveRequest(

        @Schema(description = "저장할 사이트 URL", example = "https://spring.io")
        @NotBlank(message = "invalid url")
        @URL(message = "invalid url") String url,

        @Schema(description = "저장할 스페이스 ID", example = "1")
        @NotNull Long spaceId,

        @Schema(description = "페이지 제목 (브라우저 확장 프로그램에서 탭 제목 전달). "
                + "생략하면 URL 호스트를 쓰고, AI 분석이 끝나면 AI가 추출한 제목으로 바뀜",
                example = "Spring | Home", nullable = true)
        @Size(max = 255, message = "title must be 255 characters or less")
        String title
) {

    public BookmarkSaveRequest(String url, Long spaceId) {
        this(url, spaceId, null);
    }
}
