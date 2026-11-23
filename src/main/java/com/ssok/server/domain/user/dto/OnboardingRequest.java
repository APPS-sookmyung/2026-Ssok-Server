package com.ssok.server.domain.user.dto;

import com.ssok.server.domain.user.entity.OnboardingPurpose;
import jakarta.validation.constraints.*;
import java.util.List;

public record OnboardingRequest(@NotEmpty @Size(max = 5) List<@NotNull OnboardingPurpose> purposes) {
}
