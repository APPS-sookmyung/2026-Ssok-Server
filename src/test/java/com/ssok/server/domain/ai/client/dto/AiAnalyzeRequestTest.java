package com.ssok.server.domain.ai.client.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class AiAnalyzeRequestTest {

    @Test
    void saved_at은_밀리초와_오프셋을_포함한_형식으로_변환된다() {
        LocalDateTime savedAt = LocalDateTime.of(2026, 11, 23, 10, 15, 30, 120_456_789);

        assertThat(AiAnalyzeRequest.formatSavedAt(savedAt, ZoneId.of("Asia/Seoul")))
                .isEqualTo("2026-11-23T10:15:30.120+09:00");
    }

    @Test
    void UTC도_Z가_아닌_오프셋_형식으로_변환된다() {
        LocalDateTime savedAt = LocalDateTime.of(2026, 11, 23, 1, 0, 0);

        assertThat(AiAnalyzeRequest.formatSavedAt(savedAt, ZoneOffset.UTC))
                .isEqualTo("2026-11-23T01:00:00.000+00:00");
    }
}
