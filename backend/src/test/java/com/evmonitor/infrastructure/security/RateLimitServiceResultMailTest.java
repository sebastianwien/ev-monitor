package com.evmonitor.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitServiceResultMailTest {

    @Test
    void threeResultMailsPerUserThenSkipped() {
        RateLimitService service = new RateLimitService();
        ReflectionTestUtils.setField(service, "enabled", true);
        UUID user = UUID.randomUUID();

        assertThat(service.tryConsumeResultMail(user)).isTrue();
        assertThat(service.tryConsumeResultMail(user)).isTrue();
        assertThat(service.tryConsumeResultMail(user)).isTrue();
        assertThat(service.tryConsumeResultMail(user)).isFalse();
        assertThat(service.tryConsumeResultMail(UUID.randomUUID())).isTrue();
    }

    @Test
    void disabledLimiterAlwaysAllows() {
        RateLimitService service = new RateLimitService();
        UUID user = UUID.randomUUID();

        for (int i = 0; i < 5; i++) assertThat(service.tryConsumeResultMail(user)).isTrue();
    }
}
