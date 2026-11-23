package com.ssok.server.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AccountDeleteRequest(@NotBlank @Size(max = 200) String password) {
}
