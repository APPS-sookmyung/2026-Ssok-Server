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

    @Operation(summary = "개인정보 설정", description = "개인정보 설정")
    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<ProfileUpdateResponse>> updateProfile(
            Authentication authentication, @RequestBody @Valid ProfileUpdateRequest request) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new UnauthenticatedException("authentication required");
        }

        Long userId = (Long) authentication.getPrincipal();
        ProfileUpdateResponse response = userService.updateProfile(userId, request);

        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "profile updated successfully", response));
    }
}
