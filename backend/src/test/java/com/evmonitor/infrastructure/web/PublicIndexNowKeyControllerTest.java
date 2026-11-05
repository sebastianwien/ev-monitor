package com.evmonitor.infrastructure.web;

import com.evmonitor.application.seo.SeoAuditProperties;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** Schlüsseldatei für IndexNow: öffentlich, nur mit gültigem Schlüssel, sonst 404. */
class PublicIndexNowKeyControllerTest extends AbstractIntegrationTest {

    @Autowired
    private SeoAuditProperties properties;

    @AfterEach
    void restore() {
        properties.setIndexnowKey("");
    }

    @Test
    void servesKeyAsPlainTextWithoutLogin() {
        properties.setIndexnowKey("0123456789abcdef0123456789abcdef");
        when(indexNowClient.isEnabled()).thenReturn(true);

        ResponseEntity<String> res = restTemplate.getForEntity("/api/public/seo/indexnow-key", String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).isEqualTo("0123456789abcdef0123456789abcdef");
        assertThat(res.getHeaders().getContentType().toString()).startsWith("text/plain");
    }

    @Test
    void notFoundWithoutKey() {
        when(indexNowClient.isEnabled()).thenReturn(false);

        assertThat(restTemplate.getForEntity("/api/public/seo/indexnow-key", String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
