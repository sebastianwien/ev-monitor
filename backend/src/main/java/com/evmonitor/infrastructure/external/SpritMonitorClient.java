package com.evmonitor.infrastructure.external;

import com.evmonitor.application.spritmonitor.RawFueling;
import com.evmonitor.application.spritmonitor.SpritMonitorFuelingDTO;
import com.evmonitor.application.spritmonitor.SpritMonitorVehicleDTO;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
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

    // Same settings as the spritMonitorRestTemplate's ObjectMapper — not a Spring Bean
    // to avoid displacing Spring Boot's auto-configured primary ObjectMapper.
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);

    private final RestTemplate restTemplate;
    private final String applicationId;

    public SpritMonitorClient(
        @Qualifier("spritMonitorRestTemplate") RestTemplate restTemplate,
        @Value("${spritmonitor.application.id:190e3b1080a39777f369a4e9875df3d7}") String applicationId
    ) {
        this.restTemplate = restTemplate;
        this.applicationId = applicationId;
    }

    /**
     * Fetches all vehicles from Sprit-Monitor and filters for electric vehicles (maintanktype=5)
     */
    public List<SpritMonitorVehicleDTO> getVehicles(String token) {
        String url = BASE_URL + "/vehicles.json";
        HttpEntity<String> entity = new HttpEntity<>(createHeaders(token));

        ResponseEntity<List<SpritMonitorVehicleDTO>> response = restTemplate.exchange(
            url, HttpMethod.GET, entity, new ParameterizedTypeReference<>() {}
        );

        List<SpritMonitorVehicleDTO> vehicles = response.getBody();
        if (vehicles == null) return List.of();

        return vehicles.stream()
            .filter(v -> v.mainTankType() != null && v.mainTankType() == EV_TANK_TYPE)
            .collect(Collectors.toList());
    }

    /**
     * Fetches all fuelings for a specific vehicle with pagination.
     *
     * Each entry is returned as a {@link RawFueling} pairing the verbatim JSON received
     * from the SpritMonitor API with the parsed DTO. The raw JSON is stored as-is in
     * ev_log.raw_import_data so that unknown or future SpritMonitor fields are never lost.
     */
    public List<RawFueling> getFuelings(String token, Integer vehicleId, Integer tankId) {
        List<RawFueling> allFuelings = new ArrayList<>();
        int offset = 0;
        int limit = 100;

        while (true) {
            String url = String.format("%s/vehicle/%d/tank/%d/fuelings.json?offset=%d&limit=%d",
                BASE_URL, vehicleId, tankId, offset, limit);

            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(createHeaders(token)), String.class
            );

            String body = response.getBody();
            if (body == null || body.isBlank()) break;

            List<RawFueling> batch = parseBatch(body);
            if (batch.isEmpty()) break;

            allFuelings.addAll(batch);
            offset += limit;

            if (batch.size() < limit) break;
        }

        return allFuelings;
    }

    /**
     * Parses a JSON array response into RawFueling pairs.
     * Each element's verbatim JSON (including unknown fields) is preserved alongside the parsed DTO.
     */
    private List<RawFueling> parseBatch(String jsonArray) {
        try {
            JsonNode array = MAPPER.readTree(jsonArray);
            if (!array.isArray()) return List.of();

            List<RawFueling> result = new ArrayList<>(array.size());
            for (JsonNode node : array) {
                SpritMonitorFuelingDTO dto = MAPPER.treeToValue(node, SpritMonitorFuelingDTO.class);
                result.add(new RawFueling(dto, node.toString()));
            }
            return result;
        } catch (Exception e) {
            throw new RestClientException("Failed to parse SpritMonitor fuelings response: " + e.getMessage(), e);
        }
    }

    private HttpHeaders createHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        headers.set("APPLICATION-ID", applicationId);
        return headers;
    }
}
