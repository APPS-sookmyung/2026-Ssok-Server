package com.ssok.server.domain.user.dto;

public record UserMeResponse(Long userId, String email, String name, String profileImage, boolean onboardingCompleted) {
}
