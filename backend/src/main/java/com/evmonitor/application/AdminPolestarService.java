package com.evmonitor.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

/** Admin-Sicht auf den Polestar-Testmodus, durchgereicht vom connectors-service. */
@Service
public class AdminPolestarService {

    private final RestTemplate restTemplate;
    private final String connectorsBaseUrl;
    private final String internalToken;

    public AdminPolestarService(RestTemplate restTemplate,
                                @Value("${connectors.base-url:http://connectors-service:8081}") String connectorsBaseUrl,
                                @Value("${internal.token:}") String internalToken) {
        this.restTemplate = restTemplate;
        this.connectorsBaseUrl = connectorsBaseUrl;
        this.internalToken = internalToken;
    }

    public String summary() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Token", internalToken);
        try {
            return restTemplate.exchange(connectorsBaseUrl + "/api/internal/polestar/summary",
                    HttpMethod.GET, new HttpEntity<>(headers), String.class).getBody();
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "connectors-service unreachable", e);
        }
    }
}
