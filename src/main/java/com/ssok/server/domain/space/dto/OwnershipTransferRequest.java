package com.ssok.server.domain.space.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OwnershipTransferRequest(@NotNull @Positive Long memberId) {
}
