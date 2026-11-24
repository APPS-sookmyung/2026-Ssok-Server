package com.ssok.server.domain.user.dto;

import com.ssok.server.domain.user.entity.OnboardingPurpose;
import java.time.Instant;
import java.util.List;

public record OnboardingResponse(boolean completed, List<OnboardingPurpose> purposes, Instant completedAt) {
}
