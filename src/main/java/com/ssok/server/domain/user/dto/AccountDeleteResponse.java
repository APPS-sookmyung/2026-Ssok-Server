package com.ssok.server.domain.user.dto;

public record AccountDeleteResponse(boolean accountDeleted, int vectorCleanupTaskCount) {
}
