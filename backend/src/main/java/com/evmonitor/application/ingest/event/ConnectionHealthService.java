package com.evmonitor.application.ingest.event;

import com.evmonitor.application.ingest.event.ConnectionHealthResponse.ErrorCount;
import com.evmonitor.application.ingest.event.ConnectionHealthResponse.ProviderHealth;
import com.evmonitor.domain.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Holt die Verbindungs-Gesundheit aus Connectors ({@code /api/internal/connections/summary}, R2d) für den
 * Admin-Tab "Importe". Proxy nach dem Muster von {@code AdminWebhookService}, aber ohne 502: fehlt der
 * Endpoint (Connectors vor R2d) oder ist Connectors weg, antwortet der Core mit {@code available=false}.
 */
@Slf4j
@Service
public class ConnectionHealthService {

    /** Connectors-Provider → Datenquelle, über die seine Verbindungen importieren; daraus folgt die Import-Karte. */
    private static final Map<String, DataSource> CARD_SOURCE = Map.of(
            "VW_EUDA", DataSource.EU_DATA_ACT_SYNC,
            "GOE", DataSource.WALLBOX_GOE,
            "SMARTCAR", DataSource.SMARTCAR_LIVE,
            "TESLA", DataSource.TESLA_LIVE);

    private final RestTemplate restTemplate;
    private final String connectorsBaseUrl;
    private final String internalToken;

    public ConnectionHealthService(RestTemplate restTemplate,
                                   @Value("${connectors.base-url:http://connectors-service:8081}") String connectorsBaseUrl,
                                   @Value("${internal.token:}") String internalToken) {
        this.restTemplate = restTemplate;
        this.connectorsBaseUrl = connectorsBaseUrl;
        this.internalToken = internalToken;
    }

    /** Antwort von Connectors, Feldnamen wie {@code ConnectionSummaryService.Summary}. */
    record Summary(List<ConnectorProvider> providers) {
    }

    record ConnectorProvider(String provider, int total, int active, int failing, int paused, int inactive,
                             LocalDateTime oldestLastSuccessAt, List<ErrorCount> topErrors) {
    }

    public ConnectionHealthResponse health() {
        Summary summary;
        try {
            summary = restTemplate.exchange(connectorsBaseUrl + "/api/internal/connections/summary",
                    HttpMethod.GET, internalEntity(), Summary.class).getBody();
        } catch (RestClientException e) {
            log.warn("Verbindungs-Summary aus Connectors nicht verfügbar: {}", e.getClass().getSimpleName());
            return ConnectionHealthResponse.unavailable();
        }
        if (summary == null || summary.providers() == null) return ConnectionHealthResponse.unavailable();

        List<ProviderHealth> providers = summary.providers().stream()
                .filter(p -> p.total() > 0)
                .map(ConnectionHealthService::toCard)
                .filter(Objects::nonNull)
                .toList();
        return new ConnectionHealthResponse(true, providers);
    }

    private static ProviderHealth toCard(ConnectorProvider p) {
        DataSource source = CARD_SOURCE.get(p.provider());
        if (source == null) {
            log.warn("Unbekannter Connectors-Provider in der Verbindungs-Summary: {}", p.provider());
            return null;
        }
        return new ProviderHealth(source.provider().name(), source.channel().name(),
                p.total(), p.active(), p.failing(), p.paused(), p.inactive(),
                p.oldestLastSuccessAt() == null ? null : p.oldestLastSuccessAt().atZone(ZoneId.systemDefault()).toInstant(),
                p.topErrors() == null ? List.of() : p.topErrors());
    }

    private HttpEntity<Void> internalEntity() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Token", internalToken);
        return new HttpEntity<>(headers);
    }
}
