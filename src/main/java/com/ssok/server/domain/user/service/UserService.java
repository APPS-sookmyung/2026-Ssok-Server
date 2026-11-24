package com.ssok.server.domain.user.service;

import com.ssok.server.common.exception.UnauthenticatedException;
import com.ssok.server.common.util.TimeFormatter;
import com.ssok.server.domain.user.dto.ProfileUpdateRequest;
import com.ssok.server.domain.user.dto.ProfileUpdateResponse;
import com.ssok.server.domain.user.entity.User;
import com.ssok.server.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public com.ssok.server.domain.user.dto.UserMeResponse me(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new UnauthenticatedException("authentication required"));
        return new com.ssok.server.domain.user.dto.UserMeResponse(user.getId(), user.getEmail(), user.getName(), user.getProfileImage(), user.getOnboardingCompletedAt() != null);
    }

    @Transactional(readOnly = true)
    public com.ssok.server.domain.user.dto.OnboardingResponse onboarding(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new UnauthenticatedException("authentication required"));
        return onboardingResponse(user);
    }

    @Transactional
    public com.ssok.server.domain.user.dto.OnboardingResponse submitOnboarding(Long userId, com.ssok.server.domain.user.dto.OnboardingRequest request) {
        User user = userRepository.findLockedById(userId).orElseThrow(() -> new UnauthenticatedException("authentication required"));
        user.completeOnboarding(request.purposes());
        userRepository.flush();
        return onboardingResponse(user);
    }

    private com.ssok.server.domain.user.dto.OnboardingResponse onboardingResponse(User user) {
        return new com.ssok.server.domain.user.dto.OnboardingResponse(user.getOnboardingCompletedAt() != null,
                user.getOnboardingPurposes().stream().sorted().toList(), user.getOnboardingCompletedAt());
    }

    @Transactional
    public ProfileUpdateResponse updateProfile(Long userId, ProfileUpdateRequest request) {
        User user = userRepository.findLockedById(userId)
                .orElseThrow(() -> new UnauthenticatedException("authentication required"));

        user.updateProfile(request.name().strip(), request.profileImage());
        userRepository.saveAndFlush(user);

        return new ProfileUpdateResponse(user.getId(), user.getName(), TimeFormatter.format(user.getUpdatedAt()));
    }
}
