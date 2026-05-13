package com.evmonitor.application;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

/**
 * Fetches the current live-charging state for a vehicle from the connectors-service.
 * The connectors-service owns the live telemetry data; this service proxies and maps it.
 *
 * <p>On any connectors error (timeout, 404, connection refused) we return
 * {@link LiveChargingResponse#inactive()} rather than propagating the exception -
 * the UI just shows "no active session" which is the safe fallback.
 */
@Service
public class LiveChargingService {

    private static final Logger log = LoggerFactory.getLogger(LiveChargingService.class);

    private final String connectorsBaseUrl;
    private final String internalToken;
    private final RestTemplate restTemplate;

    public LiveChargingService(
            @Qualifier("liveChargingRestTemplate") RestTemplate restTemplate,
            @Value("${connectors.base-url:http://connectors-service:8081}") String connectorsBaseUrl,
            @Value("${internal.token:}") String internalToken) {
        this.restTemplate = restTemplate;
        this.connectorsBaseUrl = connectorsBaseUrl;
        this.internalToken = internalToken;
    }

    @PostConstruct
    void validateInternalToken() {
        if (internalToken == null || internalToken.isBlank()) {
            // Fail-fast statt zur Runtime einen wirkungslosen Header zu senden, den der
            // Connectors-Service eh ablehnt. Lokal kann ueber Property
            // 'internal.token' ein Dummy gesetzt werden.
            throw new IllegalStateException(
                    "internal.token is not configured - cannot call connectors-service "
                            + "for live-charging. Set INTERNAL_TOKEN env or 'internal.token' property.");
        }
    }

    /**
     * Returns the current live-charging state for the given car.
     *
     * @param carId the car's UUID as known to the core-api
     * @return live charging data, or an inactive placeholder on error
     */
    public LiveChargingResponse getLiveCharging(UUID carId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Token", internalToken);

            ResponseEntity<LiveChargingResponse> response = restTemplate.exchange(
                    connectorsBaseUrl + "/api/internal/tesla/" + carId + "/live/charging",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    LiveChargingResponse.class);

            LiveChargingResponse body = response.getBody();
            return body != null ? body : LiveChargingResponse.inactive();
        } catch (RestClientException e) {
            log.debug("[LIVE-CHARGING] Connectors call failed for carId={}: {}", carId, e.getMessage());
            return LiveChargingResponse.inactive();
        }
    }

    /**
     * Returns a chronological power-over-time series for the given car between
     * {@code fromIso} and {@code toIso} (ISO-8601). Window is hard-capped at 24h
     * on the connectors side.
     *
     * @return live charging history, or an empty list on error
     */
    public LiveChargingHistoryResponse getLiveChargingHistory(UUID carId, String fromIso, String toIso) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Token", internalToken);

            // URL-encode the timestamp strings so a maliciously crafted "to"-Param mit
            // eingebauten Query-Trennern keine zusätzlichen Params an die Connectors-API
            // injizieren kann (defense-in-depth, Connectors validiert eh).
            String encFrom = java.net.URLEncoder.encode(fromIso, java.nio.charset.StandardCharsets.UTF_8);
            String encTo = java.net.URLEncoder.encode(toIso, java.nio.charset.StandardCharsets.UTF_8);
            String url = connectorsBaseUrl + "/api/internal/tesla/" + carId
                    + "/live/charging/history?from=" + encFrom + "&to=" + encTo;

            ResponseEntity<LiveChargingHistoryResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    LiveChargingHistoryResponse.class);

            LiveChargingHistoryResponse body = response.getBody();
            return body != null ? body : LiveChargingHistoryResponse.empty();
        } catch (RestClientException e) {
            log.debug("[LIVE-CHARGING-HISTORY] Connectors call failed for carId={}: {}", carId, e.getMessage());
            return LiveChargingHistoryResponse.empty();
        }
    }
}
