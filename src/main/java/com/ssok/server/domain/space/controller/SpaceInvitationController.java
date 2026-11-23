package com.ssok.server.domain.space.controller;

import com.ssok.server.common.exception.UnauthenticatedException;
import com.ssok.server.common.response.ApiResponse;
import com.ssok.server.domain.space.dto.*;
import com.ssok.server.domain.space.service.SpaceInvitationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "스페이스 초대", description = "로그인한 수신자의 수락 후 멤버십 생성")
@RestController
@RequestMapping("/api/spaces")
@RequiredArgsConstructor
public class SpaceInvitationController {
    private final SpaceInvitationService invitations;

    @PostMapping({"/{spaceId}/invitations", "/{spaceId}/invite"})
    public ResponseEntity<ApiResponse<InvitationResponse>> create(Authentication authentication,
            @PathVariable Long spaceId, @RequestBody @Valid InvitationCreateRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.success(201, "invitation created",
                invitations.create(userId(authentication), spaceId, request)));
    }

    @GetMapping("/{spaceId}/invitations")
    public ApiResponse<List<InvitationResponse>> list(Authentication authentication, @PathVariable Long spaceId) {
        return ApiResponse.success(200, "invitations retrieved", invitations.list(userId(authentication), spaceId));
    }

    @DeleteMapping("/{spaceId}/invitations/{invitationId}")
    public ApiResponse<Boolean> cancel(Authentication authentication, @PathVariable Long spaceId, @PathVariable Long invitationId) {
        invitations.cancel(userId(authentication), spaceId, invitationId);
        return ApiResponse.success(200, "invitation cancelled", true);
    }

    @PostMapping("/{spaceId}/invitations/{invitationId}/resend")
    public ApiResponse<InvitationResponse> resend(Authentication authentication, @PathVariable Long spaceId, @PathVariable Long invitationId) {
        return ApiResponse.success(200, "invitation reissued", invitations.resend(userId(authentication), spaceId, invitationId));
    }

    @PostMapping("/invitations/accept")
    public ApiResponse<MemberDto> accept(Authentication authentication, @RequestBody @Valid InvitationAcceptRequest request) {
        return ApiResponse.success(200, "invitation accepted", invitations.accept(userId(authentication), request.token()));
    }

    private Long userId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long id)) {
            throw new UnauthenticatedException("authentication required");
        }
        return id;
    }
}
