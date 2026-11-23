package com.ssok.server.common.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param baseUrl         AI 서버 주소
 * @param callbackBaseUrl AI 서버가 콜백을 보낼 이 Spring 서버의 외부 주소 (callback_url 생성에 사용)
 * @param callbackSecret  콜백 검증용 시크릿. prod 프로필에서는 반드시 설정해야 한다.
 * @param analysisTimeout 이 시간 동안 콜백이 오지 않은 PENDING 분석은 FAILED로 처리한다.
 */
@ConfigurationProperties(prefix = "ai.server")
public record AiServerProperties(
        String baseUrl,
        String callbackBaseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        String callbackSecret,
        Duration analysisTimeout
) {

    public static final String CALLBACK_PATH = "/api/v1/bookmarks/ai-result";

    public String callbackUrl() {
        String base = callbackBaseUrl.endsWith("/")
                ? callbackBaseUrl.substring(0, callbackBaseUrl.length() - 1)
                : callbackBaseUrl;
        return base + CALLBACK_PATH;
    }

    public boolean hasCallbackSecret() {
        return callbackSecret != null && !callbackSecret.isBlank();
    }
}
