package com.ssok.server.common.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class AiCallbackSecretValidatorTest {

    @Test
    void prod에서_시크릿이_없으면_기동을_막는다() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        assertThatThrownBy(() -> new AiCallbackSecretValidator(environment, properties("")).validate())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void prod에서_시크릿이_있으면_통과한다() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        assertThatCode(() -> new AiCallbackSecretValidator(environment, properties("secret")).validate())
                .doesNotThrowAnyException();
    }

    @Test
    void 로컬에서는_시크릿이_없어도_통과한다() {
        assertThatCode(() -> new AiCallbackSecretValidator(new MockEnvironment(), properties(null)).validate())
                .doesNotThrowAnyException();
    }

    private AiServerProperties properties(String secret) {
        return new AiServerProperties("http://ai", "http://spring", Duration.ofSeconds(3), Duration.ofSeconds(30),
                secret, Duration.ofMinutes(10));
    }
}
