package com.ssok.server.domain.user.controller;

import com.ssok.server.common.exception.UnauthenticatedException;
import com.ssok.server.common.response.ApiResponse;
import com.ssok.server.domain.user.dto.ProfileUpdateRequest;
import com.ssok.server.domain.user.dto.ProfileUpdateResponse;
import com.ssok.server.domain.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "유저", description = "유저 관련 API")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @org.springframework.web.bind.annotation.GetMapping("/me")
    public ApiResponse<com.ssok.server.domain.user.dto.UserMeResponse> me(Authentication authentication) {
        return ApiResponse.success(200, "user retrieved", userService.me(userId(authentication)));
    }

    @org.springframework.web.bind.annotation.GetMapping("/me/onboarding")
    public ApiResponse<com.ssok.server.domain.user.dto.OnboardingResponse> onboarding(Authentication authentication) {
        return ApiResponse.success(200, "onboarding retrieved", userService.onboarding(userId(authentication)));
    }

    @PutMapping("/me/onboarding")
    public ApiResponse<com.ssok.server.domain.user.dto.OnboardingResponse> onboarding(Authentication authentication,
            @RequestBody @Valid com.ssok.server.domain.user.dto.OnboardingRequest request) {
        return ApiResponse.success(200, "onboarding saved", userService.submitOnboarding(userId(authentication), request));
    }

    private Long userId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long id)) throw new UnauthenticatedException("authentication required");
        return id;
    }

    @Operation(summary = "개인정보 설정", description = "개인정보 설정")
    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<ProfileUpdateResponse>> updateProfile(
            Authentication authentication, @RequestBody @Valid ProfileUpdateRequest request) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new UnauthenticatedException("authentication required");
        }

        ProfileUpdateResponse response = userService.updateProfile(userId, request);

        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "profile updated successfully", response));
    }
}
