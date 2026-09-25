package com.evmonitor.application.ingest.event;

import com.evmonitor.application.ingest.ChargingEntry;
import com.evmonitor.application.ingest.IngestCommand;
import com.evmonitor.application.ingest.IngestDoor;
import com.evmonitor.application.ingest.IngestGateway;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.DataSource;
import com.evmonitor.domain.User;
import com.evmonitor.domain.UserRepository;
import com.evmonitor.domain.exception.NotFoundException;
import com.evmonitor.infrastructure.persistence.ingest.ImportEventRepository;
import com.evmonitor.testutil.TestDataBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code import_event} gegen das echte Schema (V190 per Flyway): Fremdschlüssel, Kontolöschung per
 * CASCADE und die Statistik-Queries, die in H2 nur angenähert laufen. Übersprungen ohne Docker.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class ImportEventPostgresIT {

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

    @Autowired IngestGateway gateway;
    @Autowired ImportEventRepository repository;
    @Autowired ImportStatsService statsService;
    @Autowired UserRepository userRepository;
    @Autowired CarRepository carRepository;

    @Test
    void gatewayEvents_areStored_aggregated_andDeletedWithTheAccount() {
        User user = userRepository.save(TestDataBuilder.createTestUser("event-pg-" + UUID.randomUUID().toString().substring(0, 8) + "@t.de"));
        Car car = carRepository.save(TestDataBuilder.createTestCar(user.getId(), CarBrand.CarModel.ID_4, new BigDecimal("77")));
        ChargingEntry entry = ChargingEntry.builder()
                .loggedAt(LocalDateTime.now().withSecond(0).withNano(0))
                .kwhCharged(new BigDecimal("20.0"))
                .build();

        gateway.ingestCharging(new IngestCommand(user.getId(), car.getId(), DataSource.EU_DATA_ACT_IMPORT,
                IngestDoor.IMPORT_BATCH, List.of(entry)));
        // Unbekanntes Auto: Zeile mit car_id NULL, sonst scheiterte sie am Fremdschlüssel
        assertThatThrownBy(() -> gateway.ingestCharging(new IngestCommand(user.getId(), UUID.randomUUID(),
                DataSource.EU_DATA_ACT_IMPORT, IngestDoor.IMPORT_BATCH, List.of(entry))))
                .isInstanceOf(NotFoundException.class);

        ImportStatsResponse stats = statsService.stats(7);
        assertThat(stats.groups()).singleElement().satisfies(g -> {
            assertThat(g.provider()).isEqualTo("VW_GROUP");
            assertThat(g.events()).isEqualTo(2);
            assertThat(g.sessionsImported()).isEqualTo(1);
            assertThat(g.outcomes()).containsEntry(ImportEventOutcome.REJECTED, 1L);
        });
        assertThat(stats.daily()).extracting(ImportStatsResponse.DailyCount::date).containsOnly(LocalDate.now());

        userRepository.delete(user);
        assertThat(repository.findAll()).isEmpty();
    }
}
