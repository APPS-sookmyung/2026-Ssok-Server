package com.ssok.server.domain.notification.event;

import java.time.Instant;

public record UserReturnedAfterAbsence(Long userId, Instant previousLoginAt) {
}
