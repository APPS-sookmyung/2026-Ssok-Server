package com.ssok.server.common.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai.server")
public record AiServerProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        String callbackSecret
) {
}
