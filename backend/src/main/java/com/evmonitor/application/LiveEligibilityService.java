package com.evmonitor.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Single source of truth for "may this user buy or upgrade to AutoSync Live?".
 * Live is a Tesla-only product, so the gate is "user has a Tesla connection on
 * record". Pairing status is intentionally NOT required here - if the connection
 * exists but the virtual key is unpaired, the user can still pay and the
 * post-purchase Settings flow will surface the repair-CTA.
 *
 * <p>Tesla connections live in the connectors-service DB (ev_connectors), not
 * the core-api DB (ev_monitor). To stay consistent with the connection-status
 * shown in the frontend, we forward the user's JWT to the connectors-service's
 * existing {@code GET /api/tesla/fleet/status} endpoint. That endpoint is the
 * same one the frontend Telemetry-Card reads from, so the eligibility check
 * cannot disagree with what the user sees in the UI.
 *
 * <p>Failure handling: on connectors-service errors or timeouts we return
 * <b>false</b>. Better to deny a payment briefly than to take money for a
 * feature we cannot deliver.
 */
@Service
public class LiveEligibilityService {

    private static final Logger log = LoggerFactory.getLogger(LiveEligibilityService.class);

    @Value("${connectors.base-url:http://connectors-service:8081}")
    private String connectorsBaseUrl;

    private final RestTemplate restTemplate = buildRestTemplate();

    private static RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);
        factory.setReadTimeout(5_000);
        return new RestTemplate(factory);
    }

    /**
     * @param authHeader the verbatim {@code Authorization} header value from the
     *                   incoming user request (e.g. {@code "Bearer eyJ..."}).
     * @return true iff the connectors-service reports an active Tesla connection
     *         for this user.
     */
    @SuppressWarnings("unchecked")
    public boolean isEligibleForLive(String authHeader) {
        if (authHeader == null || authHeader.isBlank()) return false;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", authHeader);
            var resp = restTemplate.exchange(
                    connectorsBaseUrl + "/api/tesla/fleet/status",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    Map.class);
            Map<String, Object> body = resp.getBody();
            return body != null && Boolean.TRUE.equals(body.get("connected"));
        } catch (RestClientException e) {
            log.warn("[ELIGIBILITY] Tesla connection check failed: {}", e.getMessage());
            return false;
        }
    }
}
