package com.evmonitor.infrastructure.web;

import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class PublicEmbedControllerTest extends AbstractIntegrationTest {

    private static final String URL = "/api/public/embed/model-tile?brand=Volkswagen&model=ID.3";

    @Test
    void knownModelReturnsTileWithPublicCache() {
        ResponseEntity<String> response = restTemplate.getForEntity(URL, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"modelDisplayName\":\"Volkswagen ID.3\"");
        assertThat(response.getBody()).contains("/modelle/Volkswagen/ID.3");
        assertThat(response.getHeaders().getCacheControl()).contains("max-age=3600").contains("public");
    }

    @Test
    void unknownModelReturns200WithHasDataFalse() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/public/embed/model-tile?brand=Foo&model=Bar", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"hasData\":false");
    }

    @Test
    void anyOriginMayReadTheTileWithoutCredentials() {
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin("https://www.firstev.de");
        ResponseEntity<String> response = restTemplate.exchange(URL, HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getAccessControlAllowOrigin()).isEqualTo("*");
        assertThat(response.getHeaders().getAccessControlAllowCredentials()).isFalse();
    }

    @Test
    void foreignOriginStaysBlockedOnOtherPublicEndpoints() {
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin("https://www.firstev.de");
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/public/models/Volkswagen/ID.3", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
