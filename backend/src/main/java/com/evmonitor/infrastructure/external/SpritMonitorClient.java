package com.evmonitor.infrastructure.external;

import com.evmonitor.application.spritmonitor.SpritMonitorFuelingDTO;
import com.evmonitor.application.spritmonitor.SpritMonitorVehicleDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class SpritMonitorClient {
    private static final String BASE_URL = "https://api.spritmonitor.de/v1";
    private static final int EV_TANK_TYPE = 5;

    private final RestTemplate restTemplate;
    private final String applicationId;

    public SpritMonitorClient(
        RestTemplate restTemplate,
        @Value("${spritmonitor.application.id:190e3b1080a39777f369a4e9875df3d7}") String applicationId
    ) {
        this.restTemplate = restTemplate;
        this.applicationId = applicationId;
    }

    /**
     * Fetches all vehicles from Sprit-Monitor and filters for electric vehicles (maintanktype=5)
     *
     * @param token Bearer token for Sprit-Monitor API
     * @return List of electric vehicles
     * @throws RestClientException if API call fails
     */
    public List<SpritMonitorVehicleDTO> getVehicles(String token) {
        String url = BASE_URL + "/vehicles.json";
        HttpHeaders headers = createHeaders(token);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<List<SpritMonitorVehicleDTO>> response = restTemplate.exchange(
            url,
            HttpMethod.GET,
            entity,
            new ParameterizedTypeReference<>() {}
        );

        List<SpritMonitorVehicleDTO> vehicles = response.getBody();
        if (vehicles == null) {
            return List.of();
        }

        // Filter for electric vehicles only
        return vehicles.stream()
            .filter(v -> v.mainTankType() != null && v.mainTankType() == EV_TANK_TYPE)
            .collect(Collectors.toList());
    }

    /**
     * Fetches all fuelings for a specific vehicle with pagination
     *
     * @param token Bearer token for Sprit-Monitor API
     * @param vehicleId Sprit-Monitor vehicle ID
     * @return List of all fuelings for this vehicle
     * @throws RestClientException if API call fails
     */
    public List<SpritMonitorFuelingDTO> getFuelings(String token, Integer vehicleId) {
        List<SpritMonitorFuelingDTO> allFuelings = new ArrayList<>();
        int offset = 0;
        int limit = 100;
        boolean hasMore = true;

        while (hasMore) {
            String url = String.format("%s/vehicle/%d/tank/1/fuelings.json?offset=%d&limit=%d",
                BASE_URL, vehicleId, offset, limit);

            HttpHeaders headers = createHeaders(token);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<List<SpritMonitorFuelingDTO>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                new ParameterizedTypeReference<>() {}
            );

            List<SpritMonitorFuelingDTO> batch = response.getBody();
            if (batch == null || batch.isEmpty()) {
                hasMore = false;
            } else {
                allFuelings.addAll(batch);
                offset += limit;

                // If we got less than limit, we've reached the end
                if (batch.size() < limit) {
                    hasMore = false;
                }
            }
        }

        return allFuelings;
    }

    private HttpHeaders createHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        headers.set("APPLICATION-ID", applicationId);
        return headers;
    }
}
