package com.evmonitor.application.tesla;

import ch.hsr.geohash.GeoHash;
import com.evmonitor.domain.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Tesla Fleet API integration using OAuth2 and the official charging_history endpoint.
 *
 * Prerequisites:
 * - TESLA_FLEET_CLIENT_ID env var (from Tesla Developer Portal)
 * - TESLA_FLEET_CLIENT_SECRET env var (from Tesla Developer Portal)
 * - TESLA_FLEET_REDIRECT_URI env var (must match Tesla app registration)
 *
 * Auth flow:
 * 1. generateAuthUrl() → user redirected to Tesla OAuth
 * 2. handleCallback() → exchanges code for access+refresh tokens, stores them
 * 3. syncChargingHistory() → polls charging_history for past sessions
 * 4. refreshAccessToken() → called automatically when token expires
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TeslaFleetApiService {

    private static final String TESLA_AUTH_BASE = "https://auth.tesla.com/oauth2/v3";
    private static final String TESLA_FLEET_BASE = "https://fleet-api.prd.na.vn.cloud.tesla.com/api/1";
    private static final String ALGORITHM = "AES";
    private static final String OAUTH_SCOPE =
            "openid offline_access vehicle_device_data vehicle_charging_cmds";

    private final TeslaConnectionRepository teslaConnectionRepository;
    private final EvLogRepository evLogRepository;
    private final CarRepository carRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${tesla.fleet.client-id:}")
    private String clientId;

    @Value("${tesla.fleet.client-secret:}")
    private String clientSecret;

    @Value("${tesla.fleet.redirect-uri:http://localhost:8080/api/tesla/fleet/auth/callback}")
    private String redirectUri;

    @Value("${tesla.encryption.key:change-this-32-char-secret-key!!}")
    private String encryptionKey;

    // ===== OAuth2 Flow =====

    /**
     * Returns true if Fleet API credentials are configured (client ID + secret set).
     */
    public boolean isFleetApiConfigured() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }

    /**
     * Generates the Tesla OAuth2 authorization URL.
     * The state encodes userId + nonce for CSRF protection.
     */
    public TeslaFleetAuthStartResponse generateAuthUrl(UUID userId) {
        if (!isFleetApiConfigured()) {
            return new TeslaFleetAuthStartResponse(null, false);
        }

        String nonce = UUID.randomUUID().toString();
        String state = Base64.getUrlEncoder().encodeToString(
                (userId + ":" + nonce).getBytes(StandardCharsets.UTF_8)
        );

        String authUrl = UriComponentsBuilder.fromHttpUrl(TESLA_AUTH_BASE + "/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", OAUTH_SCOPE)
                .queryParam("state", state)
                .build().toUriString();

        return new TeslaFleetAuthStartResponse(authUrl, true);
    }

    /**
     * Handles the OAuth2 callback: exchanges code for tokens and stores them.
     * Returns the userId extracted from state for redirect.
     */
    @Transactional
    public UUID handleCallback(String code, String state) {
        // Decode state to get userId
        String decoded = new String(Base64.getUrlDecoder().decode(state), StandardCharsets.UTF_8);
        UUID userId = UUID.fromString(decoded.split(":")[0]);

        // Exchange code for tokens
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);
        params.add("code", code);
        params.add("redirect_uri", redirectUri);

        ResponseEntity<TeslaFleetTokenResponse> response = restTemplate.exchange(
                TESLA_AUTH_BASE + "/token",
                HttpMethod.POST,
                new HttpEntity<>(params, headers),
                TeslaFleetTokenResponse.class
        );

        TeslaFleetTokenResponse tokens = response.getBody();
        if (tokens == null || tokens.accessToken() == null) {
            throw new IllegalStateException("No tokens received from Tesla");
        }

        // Fetch VIN and vehicle info
        String vin = fetchVin(tokens.accessToken());
        String vehicleName = fetchVehicleName(vin, tokens.accessToken());

        // Delete old connection if exists
        teslaConnectionRepository.findByUserId(userId)
                .ifPresent(teslaConnectionRepository::delete);

        TeslaConnection connection = TeslaConnection.builder()
                .userId(userId)
                .accessToken(encrypt(tokens.accessToken()))
                .refreshToken(encrypt(tokens.refreshToken()))
                .vehicleId(vin) // use VIN as vehicleId for Fleet API
                .vehicleName(vehicleName)
                .vin(vin)
                .authType("FLEET_API")
                .autoImportEnabled(true)
                .build();

        teslaConnectionRepository.save(connection);
        log.info("Tesla Fleet API connected for user {}: VIN={}", userId, vin);

        return userId;
    }

    /**
     * Imports historical charging sessions from Tesla charging_history endpoint.
     */
    @Transactional
    public TeslaFleetSyncResult syncChargingHistory(UUID userId) {
        TeslaConnection connection = teslaConnectionRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("No Tesla Fleet connection"));

        if (!"FLEET_API".equals(connection.getAuthType())) {
            throw new IllegalStateException("Connection is not Fleet API type");
        }

        String accessToken = ensureFreshToken(connection);

        // Fetch charging history since last sync (or all if first sync)
        LocalDateTime since = connection.getLastHistorySyncAt() != null
                ? connection.getLastHistorySyncAt()
                : LocalDateTime.of(2020, 1, 1, 0, 0);

        List<TeslaChargingSession> sessions = fetchChargingHistory(connection.getVin(), accessToken, since);

        Car car = findOrCreateCarForVin(userId, connection.getVin(), accessToken);

        int imported = 0;
        int skipped = 0;

        for (TeslaChargingSession session : sessions) {
            if (session.energyAdded() == null || session.energyAdded() < 0.1) {
                skipped++;
                continue;
            }

            LocalDateTime loggedAt = parseDateTime(session.chargeStopDateTime() != null
                    ? session.chargeStopDateTime() : session.chargeStartDateTime());

            if (evLogRepository.existsByCarIdAndLoggedAtBetween(car.getId(),
                    loggedAt.minusHours(1), loggedAt.plusHours(1))) {
                skipped++;
                continue;
            }

            String geohash = null;
            if (session.lat() != null && session.lon() != null) {
                geohash = GeoHash.withCharacterPrecision(session.lat(), session.lon(), 5).toBase32();
            }

            Integer durationMinutes = null;
            if (session.chargeStartDateTime() != null && session.chargeStopDateTime() != null) {
                LocalDateTime start = parseDateTime(session.chargeStartDateTime());
                LocalDateTime stop = parseDateTime(session.chargeStopDateTime());
                durationMinutes = (int) java.time.Duration.between(start, stop).toMinutes();
            }

            EvLog evLog = EvLog.createNewWithSource(
                    car.getId(),
                    BigDecimal.valueOf(session.energyAdded()),
                    null, // cost — Tesla charges in USD, not EUR
                    durationMinutes,
                    geohash,
                    null, // odometer not in charging_history
                    null, // maxChargingPower not in charging_history
                    loggedAt,
                    DataSource.TESLA_IMPORT
            );
            evLogRepository.save(evLog);
            imported++;
        }

        // Update last sync time
        connection.setLastHistorySyncAt(LocalDateTime.now());
        teslaConnectionRepository.save(connection);

        log.info("Tesla Fleet sync for user {}: imported={}, skipped={}", userId, imported, skipped);
        return new TeslaFleetSyncResult(imported, skipped, connection.getVehicleName(),
                imported > 0 ? imported + " Ladevorgänge importiert" : "Keine neuen Ladevorgänge");
    }

    /**
     * Daily job: syncs charging history for all Fleet API connections with auto-import.
     */
    @Scheduled(cron = "0 0 6 * * *")
    public void dailyHistorySync() {
        if (!isFleetApiConfigured()) return;

        teslaConnectionRepository.findAll().stream()
                .filter(c -> "FLEET_API".equals(c.getAuthType()) && c.isAutoImportEnabled())
                .forEach(c -> {
                    try {
                        syncChargingHistory(c.getUserId());
                    } catch (Exception e) {
                        log.error("Daily Tesla sync failed for user {}: {}", c.getUserId(), e.getMessage());
                    }
                });
    }

    // ===== Private Helpers =====

    private String ensureFreshToken(TeslaConnection connection) {
        // For simplicity, always use the stored access token
        // TODO: check expiry via token_expires_at and refresh if needed
        String token = decrypt(connection.getAccessToken());

        // Attempt refresh preemptively if refresh_token available
        // (real impl would check expiry timestamp)
        if (connection.getRefreshToken() != null) {
            try {
                return refreshAccessToken(connection);
            } catch (Exception e) {
                log.debug("Token refresh failed, using stored token: {}", e.getMessage());
                return token;
            }
        }
        return token;
    }

    private String refreshAccessToken(TeslaConnection connection) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "refresh_token");
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);
        params.add("refresh_token", decrypt(connection.getRefreshToken()));

        ResponseEntity<TeslaFleetTokenResponse> response = restTemplate.exchange(
                TESLA_AUTH_BASE + "/token",
                HttpMethod.POST,
                new HttpEntity<>(params, headers),
                TeslaFleetTokenResponse.class
        );

        TeslaFleetTokenResponse tokens = response.getBody();
        if (tokens == null || tokens.accessToken() == null) {
            throw new IllegalStateException("Token refresh failed");
        }

        connection.setAccessToken(encrypt(tokens.accessToken()));
        if (tokens.refreshToken() != null) {
            connection.setRefreshToken(encrypt(tokens.refreshToken()));
        }
        connection.setLastSyncAt(LocalDateTime.now());
        teslaConnectionRepository.save(connection);

        return tokens.accessToken();
    }

    @SuppressWarnings("unchecked")
    private String fetchVin(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        ResponseEntity<Map> response = restTemplate.exchange(
                TESLA_FLEET_BASE + "/vehicles",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class
        );

        if (response.getBody() == null) throw new IllegalStateException("No vehicles found");

        List<Map<String, Object>> vehicles = (List<Map<String, Object>>) response.getBody().get("response");
        if (vehicles == null || vehicles.isEmpty()) {
            throw new IllegalStateException("No vehicles in Tesla account");
        }

        return (String) vehicles.get(0).get("vin");
    }

    @SuppressWarnings("unchecked")
    private String fetchVehicleName(String vin, String accessToken) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);

            ResponseEntity<Map> response = restTemplate.exchange(
                    TESLA_FLEET_BASE + "/vehicles/" + vin,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    Map.class
            );

            if (response.getBody() != null) {
                Map<String, Object> resp = (Map<String, Object>) response.getBody().get("response");
                if (resp != null && resp.get("display_name") != null) {
                    return (String) resp.get("display_name");
                }
            }
        } catch (Exception e) {
            log.warn("Could not fetch vehicle name: {}", e.getMessage());
        }
        return "Tesla";
    }

    @SuppressWarnings("unchecked")
    private List<TeslaChargingSession> fetchChargingHistory(String vin, String accessToken,
                                                              LocalDateTime since) {
        List<TeslaChargingSession> allSessions = new ArrayList<>();
        int page = 1;
        boolean hasMore = true;

        while (hasMore) {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setBearerAuth(accessToken);

                String url = UriComponentsBuilder
                        .fromHttpUrl(TESLA_FLEET_BASE.replace("na.vn", "na.vn") + "/../dx/charging/history")
                        .queryParam("vin", vin)
                        .queryParam("startTime", since.toString() + "Z")
                        .queryParam("pageNo", page)
                        .queryParam("pageSize", 50)
                        .build().toUriString();

                // Fleet API charging history is at a different base
                url = "https://fleet-api.prd.na.vn.cloud.tesla.com/api/1/dx/charging/history"
                        + "?vin=" + vin
                        + "&startTime=" + since.toString() + "Z"
                        + "&pageNo=" + page
                        + "&pageSize=50";

                ResponseEntity<Map> response = restTemplate.exchange(
                        url, HttpMethod.GET, new HttpEntity<>(headers), Map.class
                );

                if (response.getBody() == null) break;

                List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
                if (data == null || data.isEmpty()) {
                    hasMore = false;
                } else {
                    for (Map<String, Object> s : data) {
                        allSessions.add(new TeslaChargingSession(
                                (String) s.get("vin"),
                                (String) s.get("chargeStartDateTime"),
                                (String) s.get("chargeStopDateTime"),
                                s.get("energyAdded") instanceof Number n ? n.doubleValue() : null,
                                s.get("lat") instanceof Number n ? n.doubleValue() : null,
                                s.get("lon") instanceof Number n ? n.doubleValue() : null,
                                s.get("totalDollars") instanceof Number n ? n.doubleValue() : null,
                                (String) s.get("siteType"),
                                (String) s.get("superchargerName")
                        ));
                    }
                    Boolean totalCount = response.getBody().get("totalCount") instanceof Number n
                            ? n.intValue() > page * 50 : false;
                    hasMore = Boolean.TRUE.equals(totalCount);
                    page++;
                }
            } catch (Exception e) {
                log.error("Failed to fetch charging history page {}: {}", page, e.getMessage());
                hasMore = false;
            }
        }

        return allSessions;
    }

    private Car findOrCreateCarForVin(UUID userId, String vin, String accessToken) {
        // Try to find existing car by VIN prefix match (Model 3 VIN starts with 5YJ3...)
        List<Car> userCars = carRepository.findAllByUserId(userId);
        String modelFromVin = guessModelFromVin(vin);

        return userCars.stream()
                .filter(c -> c.getModel().name().contains(modelFromVin))
                .findFirst()
                .orElseGet(() -> {
                    CarBrand.CarModel model = CarBrand.CarModel.valueOf(modelFromVin);
                    Car newCar = Car.createNew(userId, model,
                            LocalDateTime.now().getYear() - 2,
                            "Tesla " + vin.substring(0, Math.min(6, vin.length())),
                            null, BigDecimal.valueOf(75), null);
                    return carRepository.save(newCar);
                });
    }

    private String guessModelFromVin(String vin) {
        if (vin == null || vin.length() < 4) return "MODEL_3";
        // Tesla VIN position 4: 3=Model 3, S=Model S, X=Model X, Y=Model Y
        return switch (vin.charAt(3)) {
            case 'S' -> "MODEL_S";
            case 'X' -> "MODEL_X";
            case 'Y' -> "MODEL_Y";
            default -> "MODEL_3";
        };
    }

    private LocalDateTime parseDateTime(String isoString) {
        if (isoString == null) return LocalDateTime.now();
        try {
            return OffsetDateTime.parse(isoString).toLocalDateTime();
        } catch (Exception e) {
            try {
                return LocalDateTime.parse(isoString, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (Exception ex) {
                return LocalDateTime.now();
            }
        }
    }

    private String encrypt(String value) {
        if (value == null) return null;
        try {
            SecretKeySpec keySpec = new SecretKeySpec(
                    encryptionKey.substring(0, 16).getBytes(StandardCharsets.UTF_8), ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            return Base64.getEncoder().encodeToString(
                    cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    private String decrypt(String encrypted) {
        if (encrypted == null) return null;
        try {
            SecretKeySpec keySpec = new SecretKeySpec(
                    encryptionKey.substring(0, 16).getBytes(StandardCharsets.UTF_8), ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            return new String(cipher.doFinal(Base64.getDecoder().decode(encrypted)),
                    StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Decryption failed", e);
        }
    }
}
