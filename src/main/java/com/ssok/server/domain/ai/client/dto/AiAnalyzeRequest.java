package com.ssok.server.domain.ai.client.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * AI 서버 분석 요청 본문.
 *
 * @param savedAt           북마크 최초 저장 시각 (재분석 시에도 최초 저장 시각). 형식: YYYY-MM-DDTHH:mm:ss.sss±HH:mm
 * @param analysisRequestId 이번 분석 요청 ID. AI는 콜백에 그대로 돌려줘야 하며, Spring은 최신 ID의 결과만 반영한다.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AiAnalyzeRequest(
        Long bookmarkId,
        String url,
        Long spaceId,
        String savedAt,
        String callbackUrl,
        String analysisRequestId
) {

    /**
     * @param savedAt 서버 기본 시간대 기준 LocalDateTime(@CreationTimestamp 값)
     */
    public static AiAnalyzeRequest of(Long bookmarkId, String url, Long spaceId, LocalDateTime savedAt,
            String callbackUrl, String analysisRequestId) {
        return new AiAnalyzeRequest(bookmarkId, url, spaceId, formatSavedAt(savedAt, ZoneId.systemDefault()),
                callbackUrl, analysisRequestId);
    }

    static String formatSavedAt(LocalDateTime savedAt, ZoneId zone) {
        return AiTimeFormat.format(savedAt, zone);
    }
}
