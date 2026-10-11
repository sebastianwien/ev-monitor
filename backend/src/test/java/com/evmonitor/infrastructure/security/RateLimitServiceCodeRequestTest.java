package com.evmonitor.infrastructure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitServiceCodeRequestTest {

    private final RateLimitService service = new RateLimitService();

    @BeforeEach
    void enable() {
        ReflectionTestUtils.setField(service, "enabled", true);
    }

    @Test
    void threeCodesPerAddress() {
        for (int i = 0; i < 3; i++) assertThat(service.tryConsumeCodeMail("a@example.com")).isTrue();

        assertThat(service.tryConsumeCodeMail("a@example.com")).isFalse();
    }

    @Test
    void fiveCodesPerIp() {
        for (int i = 0; i < 5; i++) assertThat(service.tryConsumeCodeRequestIp("10.0.0.1")).isTrue();

        assertThat(service.tryConsumeCodeRequestIp("10.0.0.1")).isFalse();
    }
}
