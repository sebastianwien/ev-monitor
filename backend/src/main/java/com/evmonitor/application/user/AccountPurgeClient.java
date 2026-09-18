package com.evmonitor.application.user;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DSGVO-Kontolöschung: ruft {@code DELETE /api/internal/users/{id}} in den Nebendiensten
 * (Connectors, Wallbox). Jeder Fehler ist fatal für die Kontolöschung - der Aufrufer muss abbrechen,
 * damit keine Rohdaten (GPS, Tokens) ohne Wiederholungsmöglichkeit liegen bleiben.
 */
@Component
@Slf4j
public class AccountPurgeClient {

    private final RestTemplate restTemplate;
    private final String connectorsBaseUrl;
    private final String wallboxBaseUrl;
    private final String internalToken;

    @Autowired
    public AccountPurgeClient(
            @Value("${connectors.base-url:http://connectors-service:8081}") String connectorsBaseUrl,
            @Value("${wallbox.base-url:http://wallbox-service:8090}") String wallboxBaseUrl,
            @Value("${internal.token:}") String internalToken) {
        this(buildRestTemplate(), connectorsBaseUrl, wallboxBaseUrl, internalToken);
    }

    AccountPurgeClient(RestTemplate restTemplate, String connectorsBaseUrl, String wallboxBaseUrl, String internalToken) {
        this.restTemplate = restTemplate;
        this.connectorsBaseUrl = connectorsBaseUrl;
        this.wallboxBaseUrl = wallboxBaseUrl;
        this.internalToken = internalToken;
    }

    private static RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);
        factory.setReadTimeout(10_000);
        return new RestTemplate(factory);
    }

    /** Verbindungen, Telemetrie, Webhook-Rohdaten und Pre-Trips im Connectors-Service löschen. */
    public void purgeConnectors(UUID userId, List<UUID> carIds) {
        purge("connectors", connectorsBaseUrl, userId, Map.of("carIds", carIds));
    }

    /** Wallbox-Verbindungen und OCPP-Transaktionen im Wallbox-Service löschen. */
    public void purgeWallbox(UUID userId) {
        purge("wallbox", wallboxBaseUrl, userId, null);
    }

    private void purge(String service, String baseUrl, UUID userId, Map<String, Object> body) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Token", internalToken);
            headers.setContentType(MediaType.APPLICATION_JSON);
            restTemplate.exchange(baseUrl + "/api/internal/users/" + userId, HttpMethod.DELETE,
                    new HttpEntity<>(body, headers), Void.class);
            log.info("[USER] {} data purged for userId={}", service, userId);
        } catch (Exception e) {
            log.error("[USER] {} purge failed for userId={}, account deletion aborted: {}", service, userId, e.getMessage());
            throw new IllegalStateException("Account deletion aborted: " + service + " purge failed", e);
        }
    }
}
