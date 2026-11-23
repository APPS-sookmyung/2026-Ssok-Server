package com.ssok.server.common.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * 배포(prod) 환경에서 콜백 시크릿 없이 서버가 뜨지 않도록 막는다.
 * 시크릿이 비어 있으면 누구나 분석 결과를 덮어쓸 수 있기 때문이다.
 */
@Component
@RequiredArgsConstructor
public class AiCallbackSecretValidator {

    private final Environment environment;
    private final AiServerProperties properties;

    @EventListener(ApplicationReadyEvent.class)
    public void validate() {
        if (environment.acceptsProfiles(Profiles.of("prod")) && !properties.hasCallbackSecret()) {
            throw new IllegalStateException("ai.server.callback-secret (AI_CALLBACK_SECRET) must be set in prod");
        }
    }
}
