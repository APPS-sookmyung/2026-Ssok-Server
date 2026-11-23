package com.ssok.server.domain.comment.dto;

import com.ssok.server.domain.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

public record CommentAuthorDto(

        @Schema(description = "작성자 ID", example = "3")
        Long userId,

        @Schema(description = "작성자 이름", example = "홍길동")
        String name,

        @Schema(description = "프로필 이미지 URL", nullable = true)
        String profileImage
) {

    public static CommentAuthorDto from(User user) {
        return user == null ? null : new CommentAuthorDto(user.getId(), user.getName(), user.getProfileImage());
    }
}
