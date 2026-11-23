package com.ssok.server.domain.bookmark.dto;

import com.ssok.server.domain.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

public record BookmarkSaverDto(

        @Schema(description = "저장한 사용자 ID", example = "3")
        Long userId,

        @Schema(description = "저장한 사용자 이름", example = "홍길동")
        String name,

        @Schema(description = "프로필 이미지 URL", example = "https://cdn.ssok.com/profile/3.png", nullable = true)
        String profileImage
) {

    public static BookmarkSaverDto from(User user) {
        return user == null ? null : new BookmarkSaverDto(user.getId(), user.getName(), user.getProfileImage());
    }
}
