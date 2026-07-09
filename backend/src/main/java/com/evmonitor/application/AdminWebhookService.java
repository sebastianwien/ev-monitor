package com.evmonitor.application;

import com.evmonitor.domain.User;
import com.evmonitor.domain.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Proxies the raw Smartcar webhook log from the connectors-service for the admin
 * webhook inspector. The connectors-service owns the raw data; this service only
 * forwards it (page body verbatim, no re-mapping) and enriches the connection
 * dropdown with usernames, which only the core DB knows.
 */
@Service
public class AdminWebhookService {

    private final RestTemplate restTemplate;
    private final UserRepository userRepository;
    private final String connectorsBaseUrl;
    private final String internalToken;

    public AdminWebhookService(RestTemplate restTemplate,
                               UserRepository userRepository,
                               @Value("${connectors.base-url:http://connectors-service:8081}") String connectorsBaseUrl,
                               @Value("${internal.token:}") String internalToken) {
        this.restTemplate = restTemplate;
        this.userRepository = userRepository;
        this.connectorsBaseUrl = connectorsBaseUrl;
        this.internalToken = internalToken;
    }

    /** Connection row as delivered by the connectors-service. */
    public record ConnectorConnection(UUID userId, UUID carId, String smartcarVehicleId,
                                      String make, String model, Integer year, String vin) {}

    /** Dropdown entry for the admin UI: connection plus username from the core DB. */
    public record AdminConnection(String username, UUID carId, String smartcarVehicleId,
                                  String make, String model, Integer year, String vin) {}

    /** Returns the connectors-service page response verbatim (raw JSON body). */
    public String getWebhookPage(String vehicleId, int page, int size) {
        try {
            return restTemplate.exchange(
                    connectorsBaseUrl + "/api/internal/smartcar/webhook-log?vehicleId={vehicleId}&page={page}&size={size}",
                    HttpMethod.GET, internalEntity(), String.class,
                    vehicleId, page, size).getBody();
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "connectors-service unreachable", e);
        }
    }

    public List<AdminConnection> getConnections() {
        List<ConnectorConnection> raw;
        try {
            raw = restTemplate.exchange(
                    connectorsBaseUrl + "/api/internal/smartcar/webhook-log/connections",
                    HttpMethod.GET, internalEntity(),
                    new ParameterizedTypeReference<List<ConnectorConnection>>() {}).getBody();
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "connectors-service unreachable", e);
        }
        if (raw == null) return List.of();

        List<UUID> userIds = raw.stream().map(ConnectorConnection::userId).distinct().toList();
        Map<UUID, String> usernames = userRepository.findAllByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));

        return raw.stream()
                .map(c -> new AdminConnection(usernames.get(c.userId()), c.carId(), c.smartcarVehicleId(),
                        c.make(), c.model(), c.year(), c.vin()))
                .sorted(Comparator.comparing(AdminConnection::username,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
    }

    private HttpEntity<Void> internalEntity() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Token", internalToken);
        return new HttpEntity<>(headers);
    }
}
