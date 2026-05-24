package com.evmonitor.application.imports.xpeng;

import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.User;
import com.evmonitor.domain.UserRepository;
import com.evmonitor.domain.xpeng.VinUtils;
import com.evmonitor.infrastructure.persistence.xpeng.XpengConnection;
import com.evmonitor.infrastructure.persistence.xpeng.XpengConnectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class XpengConnectionService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final XpengConnectionRepository connectionRepo;
    private final CarRepository carRepository;
    private final UserRepository userRepository;

    @Transactional
    public XpengConnection grantConsent(UUID userId, UUID carId, String vin,
                                        String clientIp, String userAgent,
                                        boolean autoSync, String xpengEmail) {
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new IllegalArgumentException("Fahrzeug nicht gefunden"));
        if (!car.getUserId().equals(userId)) {
            throw new SecurityException("Dieses Fahrzeug gehört dir nicht");
        }
        if (vin == null || vin.length() != 17) {
            throw new IllegalArgumentException("VIN muss 17 Zeichen lang sein");
        }
        if (autoSync) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User nicht gefunden"));
            if (!user.canUseXpengAutoSync()) {
                throw new SecurityException("AutoSync erfordert ein AutoSync-Abo oder eine privilegierte Rolle");
            }
        }
        if (xpengEmail != null && !xpengEmail.isBlank()) {
            if (xpengEmail.length() > 255 || !EMAIL_PATTERN.matcher(xpengEmail).matches()) {
                throw new IllegalArgumentException("Ungültige XPeng-E-Mail-Adresse");
            }
        }

        String normalizedXpengEmail = (xpengEmail != null && !xpengEmail.isBlank()) ? xpengEmail.strip() : null;
        String consentVersion = autoSync
                ? XpengConnection.AUTOSYNC_CONSENT_VERSION
                : XpengConnection.CURRENT_CONSENT_VERSION;

        Optional<XpengConnection> existing = connectionRepo.findByCarId(carId);
        if (existing.isPresent()) {
            XpengConnection conn = existing.get();
            if (conn.isActive()) {
                // Update autoSync-Felder auch bei aktiver Connection (User kann AutoSync nachtraglich aktivieren)
                conn.setAutoSyncEnabled(autoSync);
                if (autoSync) conn.setConsentVersion(consentVersion);
                conn.setXpengEmail(normalizedXpengEmail);
                return connectionRepo.save(conn);
            }
            // War widerrufen - neu erteilen
            conn.setConsentGrantedAt(LocalDateTime.now());
            conn.setConsentRevokedAt(null);
            conn.setConsentIp(clientIp);
            conn.setConsentUserAgent(userAgent);
            conn.setConsentVersion(consentVersion);
            conn.setVin(vin);
            conn.setAutoSyncEnabled(autoSync);
            conn.setXpengEmail(normalizedXpengEmail);
            // routing_token bleibt unverandert (stabiler Identifier fur bestehende Reply-Mails)
            return connectionRepo.save(conn);
        }

        XpengConnection conn = XpengConnection.builder()
                .userId(userId)
                .carId(carId)
                .vin(vin)
                .consentGrantedAt(LocalDateTime.now())
                .consentIp(clientIp)
                .consentUserAgent(userAgent)
                .consentVersion(consentVersion)
                .autoSyncEnabled(autoSync)
                .xpengEmail(normalizedXpengEmail)
                .totalImportsCount(0)
                .build();
        XpengConnection saved = connectionRepo.save(conn);
        log.info("XpengConnection: consent granted user={} car={} vin={} autoSync={}",
                userId, carId, VinUtils.mask(vin), autoSync);
        return saved;
    }

    /**
     * Upgrades an existing active v1.0 connection to AutoSync (v2.0) without requiring VIN re-entry.
     * The caller must have explicitly accepted the v2.0 consent text (enforced by controller).
     */
    @Transactional
    public XpengConnection activateAutoSync(UUID userId, UUID connectionId, String xpengEmail,
                                            String clientIp, String userAgent) {
        XpengConnection conn = connectionRepo.findById(connectionId)
                .orElseThrow(() -> new IllegalArgumentException("Verbindung nicht gefunden"));
        if (!conn.getUserId().equals(userId)) {
            throw new SecurityException("Diese Verbindung gehört dir nicht");
        }
        if (!conn.isActive()) {
            throw new IllegalArgumentException("Verbindung ist nicht aktiv");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User nicht gefunden"));
        if (!user.canUseXpengAutoSync()) {
            throw new SecurityException("AutoSync erfordert ein AutoSync-Abo oder eine privilegierte Rolle");
        }
        if (xpengEmail != null && !xpengEmail.isBlank()) {
            if (xpengEmail.length() > 255 || !EMAIL_PATTERN.matcher(xpengEmail).matches()) {
                throw new IllegalArgumentException("Ungültige XPeng-E-Mail-Adresse");
            }
        }
        conn.setAutoSyncEnabled(true);
        conn.setConsentVersion(XpengConnection.AUTOSYNC_CONSENT_VERSION);
        conn.setConsentGrantedAt(LocalDateTime.now());
        conn.setConsentIp(clientIp);
        conn.setConsentUserAgent(userAgent);
        conn.setXpengEmail((xpengEmail != null && !xpengEmail.isBlank()) ? xpengEmail.strip() : null);
        XpengConnection saved = connectionRepo.save(conn);
        log.info("XpengConnection: AutoSync activated user={} connection={}", userId, connectionId);
        return saved;
    }

    @Transactional
    public void revokeConsent(UUID userId, UUID connectionId) {
        XpengConnection conn = connectionRepo.findById(connectionId)
                .orElseThrow(() -> new IllegalArgumentException("Verbindung nicht gefunden"));
        if (!conn.getUserId().equals(userId)) {
            throw new SecurityException("Diese Verbindung gehört dir nicht");
        }
        conn.setConsentRevokedAt(LocalDateTime.now());
        connectionRepo.save(conn);
        log.info("XpengConnection: consent revoked user={} connection={}", userId, connectionId);
    }

    public List<XpengConnection> getActiveConnectionsForUser(UUID userId) {
        return connectionRepo.findAllByUserIdAndConsentRevokedAtIsNull(userId);
    }

    public Optional<XpengConnection> getConnectionForCar(UUID carId) {
        return connectionRepo.findByCarId(carId);
    }

    public List<XpengConnection> getConnectionsDueForAutoRequest() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(14);
        return connectionRepo.findDueForAutoRequest(threshold);
    }
}
