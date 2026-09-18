package com.evmonitor.application.euda;

import com.evmonitor.application.imports.eudataact.EudaAutoSyncEntitlementService;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.User;
import com.evmonitor.domain.UserRepository;
import com.evmonitor.infrastructure.security.RateLimitService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Verbinden eines VW-Group-Fahrzeugs mit dem EU-Data-Act-AutoSync.
 * <p>
 * Der Core prueft Besitz, Berechtigung und Rate-Limit, loggt sich mit dem Passwort des Nutzers
 * einmal bei der VW-ID ein ({@link EudaLoginClient}) und uebergibt dem Connectors-Service nur die
 * daraus entstandenen SSO-Cookies. Das Passwort verlaesst diese Methode nicht: es wird weder
 * gespeichert noch geloggt noch an den Connector geschickt.
 */
@Service
public class EudaConnectService {

    private static final Logger log = LoggerFactory.getLogger(EudaConnectService.class);
    private static final List<String> BRANDS = List.of("volkswagen", "skoda", "audi", "seat", "cupra");

    /** Was der Connectors-Service nach dem Anlegen der Verbindung zurueckgibt - geht 1:1 ans Frontend. */
    public record ConnectionStatus(UUID carId, String brand, String email, String vin, String status,
                                   Instant lastSuccessAt, Instant historyImportedAt, String lastError) {}

    /** Uebergabe an den Connector: Identitaet plus SSO-Cookies, bewusst ohne Passwort-Feld. */
    public record SessionHandover(UUID userId, String brand, String email, Map<String, String> idpCookies) {}

    public static class NotEntitledException extends RuntimeException {
        public NotEntitledException() {
            super("Die automatische Synchronisation ist für dein Konto nicht freigeschaltet");
        }
    }

    public static class RateLimitedException extends RuntimeException {
        public RateLimitedException() {
            super("Zu viele Anmeldeversuche. Bitte warte eine Stunde.");
        }
    }

    /** Der Connector hat die Uebergabe mit einem sprechenden Code abgelehnt (Kapazitaet, Besitz, ...). */
    public static class ConnectorRejectedException extends RuntimeException {
        private final int status;
        private final String code;

        public ConnectorRejectedException(int status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }

        public int status() { return status; }
        public String code() { return code; }
    }

    private final CarRepository carRepository;
    private final UserRepository userRepository;
    private final EudaAutoSyncEntitlementService entitlement;
    private final RateLimitService rateLimit;
    private final EudaLoginClientFactory clients;
    private final RestTemplate restTemplate;
    private final String connectorsBaseUrl;
    private final String internalToken;
    private final ObjectMapper json = new ObjectMapper();

    public EudaConnectService(CarRepository carRepository,
                              UserRepository userRepository,
                              EudaAutoSyncEntitlementService entitlement,
                              RateLimitService rateLimit,
                              EudaLoginClientFactory clients,
                              @Qualifier("eudaHandoverRestTemplate") RestTemplate restTemplate,
                              @Value("${connectors.base-url:http://connectors-service:8081}") String connectorsBaseUrl,
                              @Value("${internal.token:}") String internalToken) {
        this.carRepository = carRepository;
        this.userRepository = userRepository;
        this.entitlement = entitlement;
        this.rateLimit = rateLimit;
        this.clients = clients;
        this.restTemplate = restTemplate;
        this.connectorsBaseUrl = connectorsBaseUrl;
        this.internalToken = internalToken;
    }

    public ConnectionStatus connect(UUID userId, UUID carId, String brand, String email, String password) {
        String normalizedBrand = normalizeBrand(brand);
        if (email == null || email.isBlank() || password == null || password.isEmpty()) {
            throw new IllegalArgumentException("E-Mail und Passwort sind erforderlich");
        }
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new IllegalArgumentException("Fahrzeug nicht gefunden"));
        if (!userId.equals(car.getUserId())) throw new SecurityException("Dieses Fahrzeug gehört dir nicht");
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found"));
        if (!entitlement.entitlementFor(user).entitled()) throw new NotEntitledException();
        if (!rateLimit.tryConsumeEudaLogin(userId.toString())) throw new RateLimitedException();

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        Map<String, String> idpCookies = clients.create(normalizedBrand).login(normalizedEmail, password);
        log.info("[EUDA] VW-ID-Login ok: userId={} carId={} brand={}", userId, carId, normalizedBrand);

        return handOver(carId, new SessionHandover(userId, normalizedBrand, normalizedEmail, idpCookies));
    }

    private ConnectionStatus handOver(UUID carId, SessionHandover handover) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Token", internalToken);
        try {
            ConnectionStatus status = restTemplate.exchange(
                    connectorsBaseUrl + "/api/internal/euda/cars/" + carId + "/session",
                    HttpMethod.PUT, new HttpEntity<>(handover, headers), ConnectionStatus.class).getBody();
            if (status == null) throw new EudaAuthException.PortalUnavailable("Leere Antwort vom Connectors-Service");
            return status;
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().is4xxClientError()) throw rejection(e);
            log.warn("[EUDA] Connectors-Service antwortet {} fuer carId={}", e.getStatusCode(), carId);
            throw new EudaAuthException.PortalUnavailable("Connectors-Service HTTP " + e.getStatusCode().value());
        } catch (RestClientException e) {
            log.warn("[EUDA] Connectors-Service nicht erreichbar fuer carId={}: {}", carId, e.getMessage());
            throw new EudaAuthException.PortalUnavailable("Connectors-Service nicht erreichbar");
        }
    }

    private ConnectorRejectedException rejection(HttpStatusCodeException e) {
        String code = "BAD_REQUEST";
        String message = e.getStatusText();
        try {
            JsonNode body = json.readTree(e.getResponseBodyAsString());
            code = body.path("code").asText(code);
            message = body.path("message").asText(message);
        } catch (Exception ignored) {
            // Body nicht lesbar - Status und Standardcode reichen
        }
        return new ConnectorRejectedException(e.getStatusCode().value(), code, message);
    }

    static String normalizeBrand(String brand) {
        String b = brand == null ? "" : brand.trim().toLowerCase(Locale.ROOT);
        if ("vw".equals(b)) b = "volkswagen";
        if (!BRANDS.contains(b)) throw new IllegalArgumentException("Marke wird nicht unterstützt: " + brand);
        return b;
    }
}
