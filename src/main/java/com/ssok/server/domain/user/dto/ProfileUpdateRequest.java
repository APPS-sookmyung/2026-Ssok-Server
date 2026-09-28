package com.ssok.server.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;

public record ProfileUpdateRequest(

        @Schema(description = "이름", example = "홍예빈")
        @NotBlank(message = "invalid profile information") String name,

        @Schema(description = "프로필 이미지 URL", example = "https://example.com/profile.png")
        @NotBlank(message = "invalid profile information")
        @URL(message = "invalid profile information") String profileImage
) {
}
