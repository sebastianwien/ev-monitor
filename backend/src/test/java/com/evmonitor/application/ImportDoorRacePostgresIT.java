package com.evmonitor.application;

import com.evmonitor.application.publicapi.PublicApiImportService;
import com.evmonitor.application.publicapi.PublicApiSessionRequest;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.ChargingType;
import com.evmonitor.domain.DataSource;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.EvLogRepository;
import com.evmonitor.domain.User;
import com.evmonitor.domain.UserRepository;
import com.evmonitor.testutil.TestDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataIntegrityViolationException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

/**
 * Race-Pfade beider Türen gegen Postgres mit Flyway: nur hier existiert der Unique-Constraint
 * {@code uq_ev_log_car_loggedat_datasource} (V24), die H2-Tests kennen ihn nicht. Charakterisiert
 * das heutige Verhalten vor dem IngestGateway (Herstellerarchitektur R2). Übersprungen ohne Docker.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class ImportDoorRacePostgresIT {

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

    private static final LocalDateTime RACED = LocalDateTime.of(2026, 9, 10, 10, 0);

    @Autowired PublicApiImportService importService;
    @Autowired EvLogService evLogService;
    @Autowired UserRepository userRepository;
    @Autowired CarRepository carRepository;
    @SpyBean EvLogRepository evLogRepository;

    private User user;
    private Car car;

    @BeforeEach
    void setUp() {
        user = userRepository.save(TestDataBuilder.createTestUser("race-" + UUID.randomUUID().toString().substring(0, 8) + "@t.de"));
        car = carRepository.save(TestDataBuilder.createTestCar(user.getId(), CarBrand.CarModel.MODEL_3, new BigDecimal("75.0")));
    }

    /**
     * Der Code fängt {@code DataIntegrityViolationException} beim {@code save} und zählt skipped.
     * Gemessen: das INSERT läuft erst beim Flush am Commit ({@code JpaTransactionManager.doCommit}),
     * also hinter dem {@code catch}. Die Exception fliegt aus {@code importSessions}, der ganze
     * Batch wird zurückgerollt, auch die konfliktfreie zweite Session. Das Gateway (R2) muss das
     * bewusst entscheiden (skip je Session oder Batch scheitert).
     */
    @Test
    void door1_raceOnUniqueConstraint_failsAtCommit_andRollsBackWholeBatch() {
        concurrentRowExists(DataSource.API_UPLOAD);
        dedupCheckMissesConcurrentRow();
        var request = new PublicApiSessionRequest(car.getId(),
                List.of(entry("2026-09-10T10:00:00Z"), entry("2026-09-10T11:00:00Z")));

        assertThatThrownBy(() -> importService.importSessions(user.getId(), request, DataSource.API_UPLOAD))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(evLogRepository.findAllByCarId(car.getId())).extracting(EvLog::getLoggedAt)
                .containsExactly(RACED);
    }

    /** Tür 2 hat keinen Catch: der Connector bekommt einen Fehler statt der Idempotenz-Antwort. */
    @Test
    void door2_raceOnUniqueConstraint_throws() {
        concurrentRowExists(DataSource.SMARTCAR_LIVE);
        dedupCheckMissesConcurrentRow();
        var request = new InternalEvLogRequest(car.getId(), user.getId(), new BigDecimal("20.0"), 60, RACED, null,
                null, null, "SMARTCAR_LIVE", null, "AC", false, null, null, null, 15.0, null,
                null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> evLogService.createInternalLog(request))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(evLogRepository.findAllByCarId(car.getId())).hasSize(1);
    }

    /** Die konkurrierende Transaktion hat ihre Zeile geschrieben, war beim Dedup-Check aber noch nicht sichtbar. */
    private void dedupCheckMissesConcurrentRow() {
        doReturn(false).when(evLogRepository).existsByCarIdAndLoggedAtAndDataSource(any(), any(), any());
    }

    private void concurrentRowExists(DataSource source) {
        evLogRepository.save(EvLog.createNewWithSource(car.getId(), new BigDecimal("20.0"), null, 60,
                null, null, null, null, RACED, source, ChargingType.AC, null));
    }

    private static PublicApiSessionRequest.SessionEntry entry(String date) {
        return new PublicApiSessionRequest.SessionEntry(
                date, 20.0, null, null, null, null, null, null,
                null, null, null, null, null, null, false, null, null, null);
    }
}
