package com.evmonitor.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitServicePreviewTest {

    @Test
    void thirtyPreviewsPerIpThenRefused() {
        RateLimitService service = new RateLimitService();
        ReflectionTestUtils.setField(service, "enabled", true);

        for (int i = 0; i < 30; i++) assertThat(service.tryConsumePreview("10.0.0.1")).isTrue();
        assertThat(service.tryConsumePreview("10.0.0.1")).isFalse();
        assertThat(service.tryConsumePreview("10.0.0.2")).isTrue();
    }

    @Test
    void disabledLimiterAlwaysAllows() {
        RateLimitService service = new RateLimitService();

        for (int i = 0; i < 40; i++) assertThat(service.tryConsumePreview("10.0.0.1")).isTrue();
    }
}
