package com.evmonitor.application.goe;

import com.evmonitor.domain.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executors;

/**
 * go-eCharger Cloud API polling service.
 *
 * Polls https://{serial}.api.v3.go-e.io/api/status every 30 seconds for all active connections.
 * Uses Java 21 virtual threads for concurrent polling of all devices.
 *
 * Session detection via car state machine:
 *   car 1/3 → 2 = session started (record sessionStartedAt)
 *   car 2 → 4   = session complete → create EvLog
 *   car 2 → 1   = cable pulled mid-session → create EvLog if wh > threshold
 *
 * Key API fields:
 *   car  = car state (1=Idle, 2=Charging, 3=WaitCar, 4=Complete, 5=Error)
 *   wh   = energy in Wh since car connected (resets when car disconnects)
 *   nrg  = power array; nrg[11] = total active power in 0.01kW
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GoeApiService {

    private static final String GOE_CLOUD_URL = "https://%s.api.v3.go-e.io/api/status?token=%s&filter=car,wh,nrg";
    private static final String GOE_TEST_URL  = "https://%s.api.v3.go-e.io/api/status?token=%s&filter=car";
    private static final String ALGORITHM = "AES";
    private static final int MIN_SESSION_WH = 100; // ignore sessions under 100 Wh

    private final GoeConnectionRepository goeConnectionRepository;
    private final EvLogRepository evLogRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${goe.encryption.key:change-this-32-char-secret-key!!}")
    private String encryptionKey;

    // ===== Polling Scheduler =====

    @Scheduled(fixedDelay = 30_000)
    public void pollAll() {
        List<GoeConnection> connections = goeConnectionRepository.findAllByActiveTrue();
        if (connections.isEmpty()) return;

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            connections.forEach(conn ->
                    executor.submit(() -> {
                        try {
                            pollSingle(conn);
                        } catch (Exception e) {
                            log.error("Poll failed for go-e {}: {}", conn.getSerial(), e.getMessage());
                        }
                    })
            );
        }
    }

    @Transactional
    public void pollSingle(GoeConnection connection) {
        String apiKey = decrypt(connection.getApiKey());
        String url = String.format(GOE_CLOUD_URL, connection.getSerial(), apiKey);

        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            if (response.getBody() == null) {
                recordError(connection, "Empty response from go-e Cloud");
                return;
            }

            Map<?, ?> body = response.getBody();
            Integer newState = parseInteger(body.get("car"));
            double wh       = parseDouble(body.get("wh"));
            double maxKw    = parseNrgMaxKw(body.get("nrg"));

            if (newState != null) {
                handleStateTransition(connection, connection.getCarState(), newState, wh, maxKw);
                connection.setCarState(newState);
            }
            connection.setLastPolledAt(LocalDateTime.now());
            connection.setLastPollError(null);
            goeConnectionRepository.save(connection);

        } catch (Exception e) {
            log.warn("go-e poll failed for serial {}: {}", connection.getSerial(), e.getMessage());
            recordError(connection, truncate(e.getMessage(), 500));
        }
    }

    private void handleStateTransition(GoeConnection conn, int oldState, int newState,
                                        double wh, double maxKw) {
        // Transition to charging: record session start
        if (oldState != 2 && newState == 2) {
            conn.setSessionStartedAt(LocalDateTime.now());
            log.debug("go-e {}: charging session started", conn.getSerial());
        }

        // Transition away from charging: end session
        boolean wasCharging = (oldState == 2);
        boolean sessionOver = (newState == 4 || newState == 1 || newState == 5);
        if (wasCharging && sessionOver && conn.getSessionStartedAt() != null) {
            if (wh >= MIN_SESSION_WH) {
                saveEvLog(conn, wh, maxKw);
            } else {
                log.debug("go-e {}: session too short ({} Wh) — skipped", conn.getSerial(), wh);
            }
            conn.setSessionStartedAt(null);
        }
    }

    private void saveEvLog(GoeConnection conn, double wh, double maxKw) {
        LocalDateTime sessionStart = conn.getSessionStartedAt();
        LocalDateTime now = LocalDateTime.now();
        int durationMinutes = Math.max(1,
                (int) java.time.Duration.between(sessionStart, now).toMinutes());

        BigDecimal kwhCharged = BigDecimal.valueOf(wh / 1000.0).setScale(3, RoundingMode.HALF_UP);

        EvLog evLog = EvLog.createNewWithSource(
                conn.getCarId(),
                kwhCharged,
                null,                   // cost not known (go-e doesn't know tariff)
                durationMinutes,
                null,                   // no GPS from Cloud API (privacy-by-design)
                null,                   // no odometer
                maxKw > 0 ? BigDecimal.valueOf(maxKw).setScale(2, RoundingMode.HALF_UP) : null,
                sessionStart,
                DataSource.WALLBOX_GOE
        );

        evLogRepository.save(evLog);
        log.info("go-e {}: EvLog saved — {} kWh, {} min", conn.getSerial(), kwhCharged, durationMinutes);
    }

    // ===== Connection Management =====

    @Transactional
    public GoeConnectionStatus connect(UUID userId, UUID carId, String serial,
                                        String rawApiKey, String displayName) {
        if (goeConnectionRepository.existsBySerial(serial)) {
            goeConnectionRepository.findBySerial(serial).ifPresent(existing -> {
                if (!existing.getUserId().equals(userId)) {
                    throw new IllegalArgumentException(
                            "Seriennummer ist bereits bei einem anderen Account registriert.");
                }
                goeConnectionRepository.delete(existing);
            });
        }

        // Test the connection before saving
        String error = testConnection(serial, rawApiKey);
        if (error != null) {
            throw new IllegalArgumentException("Verbindungstest fehlgeschlagen: " + error);
        }

        GoeConnection conn = GoeConnection.builder()
                .userId(userId)
                .carId(carId)
                .serial(serial.trim())
                .apiKey(encrypt(rawApiKey.trim()))
                .displayName(displayName != null && !displayName.isBlank() ? displayName : serial)
                .build();

        goeConnectionRepository.save(conn);
        log.info("go-e connected for user {}: serial={}", userId, serial);

        return buildStatus(conn);
    }

    @Transactional
    public void disconnect(UUID connectionId, UUID userId) {
        goeConnectionRepository.findById(connectionId)
                .filter(c -> c.getUserId().equals(userId))
                .ifPresent(c -> {
                    c.setActive(false);
                    goeConnectionRepository.save(c);
                    log.info("go-e {} disconnected for user {}", c.getSerial(), userId);
                });
    }

    public List<GoeConnectionStatus> getConnectionsForUser(UUID userId) {
        return goeConnectionRepository.findAllByUserId(userId).stream()
                .filter(GoeConnection::isActive)
                .map(this::buildStatus)
                .toList();
    }

    // ===== Helpers =====

    private String testConnection(String serial, String apiKey) {
        try {
            String url = String.format(GOE_TEST_URL, serial, apiKey);
            ResponseEntity<Map> resp = restTemplate.getForEntity(url, Map.class);
            return resp.getStatusCode().is2xxSuccessful() ? null : "HTTP " + resp.getStatusCode();
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    private void recordError(GoeConnection conn, String error) {
        conn.setLastPollError(error);
        conn.setLastPolledAt(LocalDateTime.now());
        goeConnectionRepository.save(conn);
    }

    private GoeConnectionStatus buildStatus(GoeConnection c) {
        return new GoeConnectionStatus(c.getId(), c.getSerial(),
                c.getDisplayName(), c.isActive(), c.getCarState(), c.getLastPollError());
    }

    private Integer parseInteger(Object v) {
        if (v instanceof Number n) return n.intValue();
        return null;
    }

    private double parseDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        return 0.0;
    }

    private double parseNrgMaxKw(Object v) {
        // nrg[11] = total active power in 0.01 kW
        if (v instanceof List<?> list && list.size() > 11 && list.get(11) instanceof Number n) {
            return n.doubleValue() / 100.0;
        }
        return 0.0;
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }

    private String encrypt(String value) {
        try {
            SecretKeySpec key = new SecretKeySpec(
                    encryptionKey.substring(0, 16).getBytes(StandardCharsets.UTF_8), ALGORITHM);
            Cipher c = Cipher.getInstance(ALGORITHM);
            c.init(Cipher.ENCRYPT_MODE, key);
            return Base64.getEncoder().encodeToString(c.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    private String decrypt(String encrypted) {
        try {
            SecretKeySpec key = new SecretKeySpec(
                    encryptionKey.substring(0, 16).getBytes(StandardCharsets.UTF_8), ALGORITHM);
            Cipher c = Cipher.getInstance(ALGORITHM);
            c.init(Cipher.DECRYPT_MODE, key);
            return new String(c.doFinal(Base64.getDecoder().decode(encrypted)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Decryption failed", e);
        }
    }
}
