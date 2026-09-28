package com.ssok.server.domain.tag.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record TagDto(

        @Schema(description = "태그명", example = "Java")
        String tagName,

        @Schema(description = "해당 태그가 붙은 북마크 수", example = "24")
        long bookmarkCount
) {
}
