package com.ssok.server.domain.bookmark.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.URL;

public record BookmarkSaveRequest(

        @Schema(description = "저장할 사이트 URL", example = "https://spring.io")
        @NotBlank(message = "invalid url")
        @URL(message = "invalid url") String url,

        @Schema(description = "저장할 스페이스 ID", example = "1")
        @NotNull Long spaceId
) {
}
