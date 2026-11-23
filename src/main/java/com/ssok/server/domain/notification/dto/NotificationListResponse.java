package com.ssok.server.domain.notification.dto;

import java.util.List;

public record NotificationListResponse(List<NotificationResponse> notifications, int page, int size, long totalElements, int totalPages) {
}
