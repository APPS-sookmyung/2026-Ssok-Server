package com.ssok.server.domain.ai.vector;

import java.util.List;

public record VectorSyncRequestedEvent(List<Long> bookmarkIds) {
}
