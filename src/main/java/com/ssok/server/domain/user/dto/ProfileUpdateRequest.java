package com.ssok.server.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;

public record ProfileUpdateRequest(

        @Schema(description = "이름", example = "홍예빈")
        @NotBlank(message = "invalid profile information") @jakarta.validation.constraints.Size(max = 100) String name,

        @Schema(description = "프로필 이미지 URL", example = "https://example.com/profile.png")
        @jakarta.validation.constraints.Size(max = 1000)
        @URL(message = "invalid profile information") String profileImage
) {
}
