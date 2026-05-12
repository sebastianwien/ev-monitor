package com.evmonitor.application;

import com.evmonitor.infrastructure.web.LiveChargingResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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

    @Value("${connectors.base-url:http://connectors-service:8081}")
    private String connectorsBaseUrl;

    @Value("${internal.token:}")
    private String internalToken;

    private final RestTemplate restTemplate = buildRestTemplate();

    private static RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);
        factory.setReadTimeout(5_000);
        return new RestTemplate(factory);
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
}
