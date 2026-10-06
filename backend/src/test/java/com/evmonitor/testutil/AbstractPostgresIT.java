package com.evmonitor.testutil;

import com.evmonitor.domain.EvLogRepository;
import com.evmonitor.domain.EvTripRepository;
import com.evmonitor.infrastructure.external.SpritMonitorClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Basis für Tests gegen echtes, migriertes Postgres.
 *
 * <p>Alle Unterklassen teilen sich einen Spring-Context und damit eine Datenbank pro Test-JVM.
 * Tests müssen ihre Daten deshalb selbst abgrenzen (eigene User, Autos, IDs) und dürfen keine
 * leere Datenbank voraussetzen. Wer globale Auswertungen prüft, braucht weiter eine eigene
 * Datenbank über {@link SharedPostgres} direkt.
 *
 * <p>Spies und Mocks stehen hier zentral, damit Unterklassen keinen eigenen Context erzwingen.
 * Spies verhalten sich ohne Stubbing wie die echten Beans und werden nach jedem Test zurückgesetzt.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
public abstract class AbstractPostgresIT {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        SharedPostgres.registerMigrated(registry);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @MockitoSpyBean
    protected EvLogRepository evLogRepository;

    @MockitoSpyBean
    protected EvTripRepository tripRepository;

    @MockitoBean
    protected SpritMonitorClient spritMonitorClient;
}
