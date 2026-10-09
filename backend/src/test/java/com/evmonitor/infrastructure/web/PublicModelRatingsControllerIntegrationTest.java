package com.evmonitor.infrastructure.web;

import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/** Die Bewertungen einer Modellseite sind ohne Login lesbar und cachebar. */
class PublicModelRatingsControllerIntegrationTest extends AbstractIntegrationTest {

    @Test
    void withoutLogin_returnsRatings_withPublicCacheHeader() {
        ResponseEntity<String> res = restTemplate.getForEntity("/api/public/models/Volkswagen/ID.3/ratings", String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getHeaders().getCacheControl()).contains("public").contains("max-age=600");
        assertThat(res.getBody()).contains("\"count\"");
    }

    @Test
    void unknownModel_isNotFound() {
        assertThat(restTemplate.getForEntity("/api/public/models/Volkswagen/ID.99/ratings", String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
