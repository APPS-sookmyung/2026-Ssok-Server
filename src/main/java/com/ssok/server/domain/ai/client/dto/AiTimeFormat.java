package com.ssok.server.domain.ai.client.dto;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * AI 서버와 주고받는 시각 형식: YYYY-MM-DDTHH:mm:ss.sss±HH:mm (UTC도 Z가 아닌 +00:00).
 * LocalDateTime은 서버 기본 시간대(Asia/Seoul) 기준으로 해석한다.
 */
public final class AiTimeFormat {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSxxx");

    private AiTimeFormat() {
    }

    public static String format(LocalDateTime time) {
        return format(time, ZoneId.systemDefault());
    }

    static String format(LocalDateTime time, ZoneId zone) {
        return time == null ? null : time.atZone(zone).toOffsetDateTime().format(FORMAT);
    }
}
