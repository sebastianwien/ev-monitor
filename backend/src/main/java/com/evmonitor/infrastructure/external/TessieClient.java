package com.evmonitor.infrastructure.external;

import com.evmonitor.application.tessie.TessieVehicleDTO;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class TessieClient {

    private static final String BASE_URL = "https://api.tessie.com";
    // No documented max - 10000 covers any realistic fleet history per VIN
    private static final int FETCH_LIMIT = 10_000;

    private final RestTemplate restTemplate;

    public List<TessieVehicleDTO> getVehicles(String token) {
        String url = BASE_URL + "/vehicles";
        JsonNode body = get(url, token);

        List<TessieVehicleDTO> result = new ArrayList<>();
        for (JsonNode v : body.path("results")) {
            String vin = v.path("vin").asText(null);
            boolean isActive = v.path("is_active").asBoolean(true);
            String displayName = v.path("last_state").path("display_name").asText(vin);
            if (vin != null) {
                result.add(new TessieVehicleDTO(vin, displayName, isActive));
            }
        }
        return result;
    }

    public List<JsonNode> getDrives(String token, String vin) {
        String url = String.format("%s/%s/drives?limit=%d&from=0&distance_format=km", BASE_URL, vin, FETCH_LIMIT);
        JsonNode body = get(url, token);
        return toList(body.path("results"));
    }

    public List<JsonNode> getCharges(String token, String vin) {
        String url = String.format("%s/%s/charges?limit=%d&from=0&distance_format=km", BASE_URL, vin, FETCH_LIMIT);
        JsonNode body = get(url, token);
        return toList(body.path("results"));
    }

    private JsonNode get(String url, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        JsonNode body = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class).getBody();
        if (body == null) {
            throw new IllegalStateException("Tessie API returned empty response for " + url);
        }
        return body;
    }

    private List<JsonNode> toList(JsonNode arrayNode) {
        List<JsonNode> list = new ArrayList<>();
        if (arrayNode.isArray()) {
            arrayNode.forEach(list::add);
        }
        return list;
    }
}
