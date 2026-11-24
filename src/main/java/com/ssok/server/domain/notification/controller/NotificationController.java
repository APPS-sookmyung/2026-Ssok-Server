package com.ssok.server.domain.notification.controller;

import com.ssok.server.common.exception.UnauthenticatedException;
import com.ssok.server.common.response.ApiResponse;
import com.ssok.server.domain.notification.dto.NotificationListResponse;
import com.ssok.server.domain.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "알림", description = "내 알림 목록·읽음·미읽음 개수")
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService notifications;
    public record UnreadCount(long unreadCount) {}
    public record ReadAllResult(int updatedCount) {}

    @GetMapping
    public ApiResponse<NotificationListResponse> list(Authentication auth, @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(200, "notifications retrieved", notifications.list(userId(auth), unreadOnly, page, size));
    }

    @GetMapping("/unread-count")
    public ApiResponse<UnreadCount> unreadCount(Authentication auth) {
        return ApiResponse.success(200, "unread count retrieved", new UnreadCount(notifications.unreadCount(userId(auth))));
    }

    @PatchMapping("/{notificationId}/read")
    public ApiResponse<Boolean> read(Authentication auth, @PathVariable Long notificationId) {
        notifications.read(userId(auth), notificationId);
        return ApiResponse.success(200, "notification read", true);
    }

    @PatchMapping("/read-all")
    public ApiResponse<ReadAllResult> readAll(Authentication auth) {
        return ApiResponse.success(200, "notifications read", new ReadAllResult(notifications.readAll(userId(auth))));
    }

    private Long userId(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof Long id)) throw new UnauthenticatedException("authentication required");
        return id;
    }
}
