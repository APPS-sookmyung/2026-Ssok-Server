package com.ssok.server.domain.ai.vector;

import java.util.List;

public record VectorDeletionRequestedEvent(List<Long> bookmarkIds) {
}
