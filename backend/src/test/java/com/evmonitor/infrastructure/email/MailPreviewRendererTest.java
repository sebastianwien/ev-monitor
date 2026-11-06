package com.evmonitor.infrastructure.email;

import com.evmonitor.application.mail.MailType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class MailPreviewRendererTest {

    @ParameterizedTest
    @EnumSource(MailType.class)
    void everyMailing_rendersWithoutOpenPlaceholders(MailType type) {
        String html = MailPreviewRenderer.render(type);

        assertThat(html).isNotBlank().doesNotContain("{{").doesNotContain("}}");
    }

    @org.junit.jupiter.api.Test
    void knownPlaceholders_getSampleValues_andLinksPointNowhere() {
        String html = MailPreviewRenderer.render(MailType.RE_ENGAGEMENT);

        assertThat(html).contains("Max").contains("href=\"#\"");
    }
}
