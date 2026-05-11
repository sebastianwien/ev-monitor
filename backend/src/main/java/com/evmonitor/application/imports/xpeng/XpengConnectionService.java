package com.evmonitor.application.imports.xpeng;

import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarRepository;
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

@Service
@Slf4j
@RequiredArgsConstructor
public class XpengConnectionService {

    private final XpengConnectionRepository connectionRepo;
    private final CarRepository carRepository;

    @Transactional
    public XpengConnection grantConsent(UUID userId, UUID carId, String vin,
                                          String clientIp, String userAgent) {
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new IllegalArgumentException("Fahrzeug nicht gefunden"));
        if (!car.getUserId().equals(userId)) {
            throw new SecurityException("Dieses Fahrzeug gehört dir nicht");
        }
        // Light VIN check - the strict one happens during import against the actual file.
        if (vin == null || vin.length() != 17) {
            throw new IllegalArgumentException("VIN muss 17 Zeichen lang sein");
        }

        Optional<XpengConnection> existing = connectionRepo.findByCarId(carId);
        if (existing.isPresent()) {
            XpengConnection conn = existing.get();
            if (conn.isActive()) return conn;
            // Was revoked previously - re-grant.
            conn.setConsentGrantedAt(LocalDateTime.now());
            conn.setConsentRevokedAt(null);
            conn.setConsentIp(clientIp);
            conn.setConsentUserAgent(userAgent);
            conn.setConsentVersion(XpengConnection.CURRENT_CONSENT_VERSION);
            conn.setVin(vin);
            return connectionRepo.save(conn);
        }

        XpengConnection conn = XpengConnection.builder()
                .userId(userId)
                .carId(carId)
                .vin(vin)
                .consentGrantedAt(LocalDateTime.now())
                .consentIp(clientIp)
                .consentUserAgent(userAgent)
                .consentVersion(XpengConnection.CURRENT_CONSENT_VERSION)
                .totalImportsCount(0)
                .build();
        XpengConnection saved = connectionRepo.save(conn);
        log.info("XpengConnection: consent granted user={} car={} vin={}",
                userId, carId, VinUtils.mask(vin));
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
}
