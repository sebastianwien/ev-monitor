package com.evmonitor.application.user;

import com.evmonitor.domain.AuthProvider;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.CarStatus;
import com.evmonitor.infrastructure.persistence.CarEntity;
import com.evmonitor.infrastructure.persistence.EvLogEntity;
import com.evmonitor.infrastructure.persistence.JpaCarRepository;
import com.evmonitor.infrastructure.persistence.JpaEvLogRepository;
import com.evmonitor.infrastructure.persistence.JpaUserRepository;
import com.evmonitor.infrastructure.persistence.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DSGVO-Kontolöschung gegen eine echte PostgreSQL mit den Produktions-Migrationen: beweist, dass der
 * ON DELETE CASCADE von app_user die vorher anonymisierten Autos und Ladevorgänge NICHT mitreißt und
 * dass kein personenbezogenes Feld übrig bleibt. Mockito kann das nicht abdecken, weil die Löschung
 * über DB-Constraints läuft. Übersprungen ohne Docker.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class AccountDeletionAnonymizationIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired AccountAnonymizationService anonymizationService;
    @Autowired JpaUserRepository userRepository;
    @Autowired JpaCarRepository carRepository;
    @Autowired JpaEvLogRepository evLogRepository;
    @Autowired JdbcTemplate jdbc;

    private UUID userId;
    private UUID carId;
    private UUID otherUserId;
    private UUID otherCarId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        carId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
        otherCarId = UUID.randomUUID();
        // Der Kontext (und damit die DB) wird zwischen den Tests wiederverwendet: eindeutige Werte je Test
        String tag = UUID.randomUUID().toString().substring(0, 8);
        userRepository.save(user(userId, "anon-" + tag + "@example.com", "anon" + tag, "A" + tag.substring(0, 7).toUpperCase()));
        userRepository.save(user(otherUserId, "other-" + tag + "@example.com", "other" + tag, "B" + tag.substring(0, 7).toUpperCase()));
        carRepository.save(car(carId, userId, "B-AN 1234"));
        carRepository.save(car(otherCarId, otherUserId, "B-OT 9999"));
        evLogRepository.save(log(carId, LocalDateTime.of(2026, 3, 10, 18, 42), "u33dc1"));
        evLogRepository.save(log(carId, LocalDateTime.of(2026, 3, 10, 7, 5), "u33dc1"));
        evLogRepository.save(log(otherCarId, LocalDateTime.of(2026, 3, 11, 9, 0), "u33dc2"));
    }

    @Test
    void anonymizeThenDeleteUser_keepsCarAndLogsWithoutPersonalData() {
        List<UUID> carIds = anonymizationService.anonymizeCarsOf(userId);
        assertEquals(List.of(carId), carIds);

        // Nur den User löschen - der DB-CASCADE läuft wie in Produktion
        userRepository.deleteById(userId);

        assertFalse(userRepository.existsById(userId), "User muss weg sein");
        CarEntity car = carRepository.findById(carId).orElseThrow(() -> new AssertionError("Auto wurde vom CASCADE mitgelöscht"));
        assertNull(car.getUserId());
        assertNull(car.getLicensePlate());
        assertNotNull(car.getAnonymizedAt());
        assertEquals(2023, car.getYear(), "Baujahr bleibt für die Statistik");

        List<EvLogEntity> logs = evLogRepository.findAllByCarId(carId);
        assertEquals(2, logs.size(), "Logs müssen erhalten bleiben");
        for (EvLogEntity l : logs) {
            assertNull(l.getGeohash());
            assertNull(l.getRawImportData());
            assertEquals(0, l.getLoggedAt().getHour(), "Zeitpunkt auf den Tag gerundet");
            assertEquals(new BigDecimal("45.50"), l.getKwhCharged().setScale(2));
        }
        // Reihenfolge innerhalb des Tages bleibt über den Minutenversatz stabil
        assertEquals(2, logs.stream().map(l -> l.getLoggedAt().getMinute()).distinct().count());
    }

    @Test
    void anonymizeThenDeleteUser_leavesOtherUsersUntouched() {
        anonymizationService.anonymizeCarsOf(userId);
        userRepository.deleteById(userId);

        assertTrue(userRepository.existsById(otherUserId));
        CarEntity other = carRepository.findById(otherCarId).orElseThrow();
        assertEquals(otherUserId, other.getUserId());
        assertEquals("B-OT 9999", other.getLicensePlate());
        assertEquals("u33dc2", evLogRepository.findAllByCarId(otherCarId).get(0).getGeohash());
    }

    @Test
    void anonymize_alsoCoversSoftDeletedCars() {
        // DSGVO: ein Auto, das im Papierkorb liegt, darf den Personenbezug
        // nicht ueberleben, wenn der User sein Konto loescht.
        CarEntity softDeleted = carRepository.findById(carId).orElseThrow();
        softDeleted.setDeletedAt(LocalDateTime.now());
        carRepository.save(softDeleted);

        List<UUID> carIds = anonymizationService.anonymizeCarsOf(userId);

        assertEquals(List.of(carId), carIds, "Soft-geloeschtes Auto muss mit anonymisiert werden");
        CarEntity after = carRepository.findById(carId).orElseThrow();
        assertNull(after.getUserId(), "user_id muss weg sein");
        assertNull(after.getLicensePlate(), "Kennzeichen muss weg sein");
    }

    @Test
    void anonymize_hardDeletesSoftDeletedLogs() {
        // DSGVO: ein soft-geloeschter Ladevorgang ist fuer die Anonymisierung unsichtbar
        // (SQLRestriction) und wuerde Geohash und Rohdaten behalten. Nach der Kontoloeschung
        // braucht niemand den Tombstone mehr (kein Sync mehr), also weg damit.
        UUID tombstoneId = evLogRepository.findAllByCarId(carId).get(0).getId();
        evLogRepository.softDelete(tombstoneId, LocalDateTime.now());

        anonymizationService.anonymizeCarsOf(userId);

        Integer tombstones = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ev_log WHERE car_id = ? AND deleted_at IS NOT NULL", Integer.class, carId);
        assertEquals(0, tombstones, "Tombstones muessen hart geloescht sein");
        Integer withId = jdbc.queryForObject("SELECT COUNT(*) FROM ev_log WHERE id = ?", Integer.class, tombstoneId);
        assertEquals(0, withId);
        List<EvLogEntity> kept = evLogRepository.findAllByCarId(carId);
        assertEquals(1, kept.size(), "Sichtbare Logs bleiben anonymisiert erhalten");
        assertNull(kept.get(0).getGeohash(), "und ohne Geohash");
    }

    @Test
    void deleteUserWithoutAnonymization_stillCascades() {
        // Sicherheitsnetz: der CASCADE ist weiterhin aktiv für Autos mit Besitzer
        userRepository.deleteById(otherUserId);
        assertFalse(carRepository.existsById(otherCarId));
    }

    private static UserEntity user(UUID id, String email, String username, String referral) {
        UserEntity u = new UserEntity();
        u.setId(id); u.setEmail(email); u.setUsername(username); u.setPasswordHash("x");
        u.setAuthProvider(AuthProvider.LOCAL); u.setRole("USER"); u.setEmailVerified(true); u.setSeedData(false);
        u.setReferralCode(referral); u.setCreatedAt(LocalDateTime.now()); u.setUpdatedAt(LocalDateTime.now());
        return u;
    }

    private static CarEntity car(UUID id, UUID userId, String plate) {
        CarEntity c = new CarEntity();
        c.setId(id); c.setUserId(userId); c.setModel(CarBrand.CarModel.MODEL_3); c.setYear(2023);
        c.setLicensePlate(plate); c.setTrim("Long Range"); c.setCustomNetCapacityKwh(new BigDecimal("75.0"));
        c.setPowerKw(new BigDecimal("283")); c.setStatus(CarStatus.ACTIVE);
        c.setCreatedAt(LocalDateTime.now()); c.setUpdatedAt(LocalDateTime.now());
        return c;
    }

    private static EvLogEntity log(UUID carId, LocalDateTime at, String geohash) {
        EvLogEntity l = new EvLogEntity();
        l.setId(UUID.randomUUID()); l.setCarId(carId); l.setKwhCharged(new BigDecimal("45.5"));
        l.setCostEur(new BigDecimal("18.20")); l.setGeohash(geohash); l.setChargeDurationMinutes(120);
        l.setLoggedAt(at); l.setDataSource("USER_LOGGED"); l.setIncludeInStatistics(true); l.setChargingType("AC");
        l.setRawImportData("{\"raw\":true}"); l.setCreatedAt(LocalDateTime.now()); l.setUpdatedAt(LocalDateTime.now());
        return l;
    }
}
