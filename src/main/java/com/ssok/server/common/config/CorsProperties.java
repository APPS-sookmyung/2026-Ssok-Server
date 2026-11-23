package com.ssok.server.common.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param allowedOrigins 웹 프론트 주소와 브라우저 확장 프로그램 origin(chrome-extension://{확장 ID}).
 *                       패턴(https://*.ssok.app)도 허용한다.
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null
                ? List.of()
                : allowedOrigins.stream().map(String::strip).filter(origin -> !origin.isEmpty()).toList();
    }
}
