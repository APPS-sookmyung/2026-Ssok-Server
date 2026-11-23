package com.ssok.server.domain.user.controller;

import com.ssok.server.common.exception.UnauthenticatedException;
import com.ssok.server.common.response.ApiResponse;
import com.ssok.server.domain.user.dto.*;
import com.ssok.server.domain.user.service.AccountDeletionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class AccountController {
    private final AccountDeletionService accounts;
    @DeleteMapping
    public ApiResponse<AccountDeleteResponse> delete(Authentication authentication, @RequestBody @Valid AccountDeleteRequest request) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long id)) throw new UnauthenticatedException("authentication required");
        return ApiResponse.success(200, "account deleted; vector cleanup scheduled when needed", accounts.delete(id, request.password()));
    }
}
