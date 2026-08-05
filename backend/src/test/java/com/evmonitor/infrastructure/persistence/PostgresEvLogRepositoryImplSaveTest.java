package com.evmonitor.infrastructure.persistence;

import com.evmonitor.domain.DataSource;
import com.evmonitor.domain.EnergyMeasurementType;
import com.evmonitor.domain.EvLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression: Spalten, die das Domain-Modell nicht kennt, duerfen ein Update nicht ueberleben-
 * los werden. {@code power_curve_points} und {@code telemetry_extras} existieren nur auf der
 * Entity; wird beim Speichern eine frische Entity gebaut, schreibt JPA sie auf NULL zurueck.
 *
 * <p>Realer Schaden: jede nachtraegliche Log-Aenderung (Kosten eintragen, Brutto/Netto
 * ergaenzen, Ladekarte zuordnen) hat die gespeicherte Ladekurve geloescht.
 */
class PostgresEvLogRepositoryImplSaveTest {

    private static final String CURVE_JSON = "[{\"ts\":1785686565060,\"kw\":252.107}]";
    private static final String EXTRAS_JSON = "{\"foo\":\"bar\"}";

    private JpaEvLogRepository jpaRepository;
    private PostgresEvLogRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        jpaRepository = mock(JpaEvLogRepository.class);
        repository = new PostgresEvLogRepositoryImpl(jpaRepository);
        when(jpaRepository.save(any(EvLogEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void save_keepsPowerCurveOfExistingRow() {
        UUID id = UUID.randomUUID();
        when(jpaRepository.findById(id)).thenReturn(Optional.of(persistedRow(id)));

        repository.save(domainLog(id).toBuilder().costEur(new java.math.BigDecimal("12.34")).build());

        assertThat(savedEntity().getPowerCurvePoints()).isEqualTo(CURVE_JSON);
    }

    @Test
    void save_keepsTelemetryExtrasOfExistingRow() {
        UUID id = UUID.randomUUID();
        when(jpaRepository.findById(id)).thenReturn(Optional.of(persistedRow(id)));

        repository.save(domainLog(id));

        assertThat(savedEntity().getTelemetryExtras()).isEqualTo(EXTRAS_JSON);
    }

    @Test
    void save_writesDomainFieldsOntoExistingRow() {
        UUID id = UUID.randomUUID();
        when(jpaRepository.findById(id)).thenReturn(Optional.of(persistedRow(id)));

        repository.save(domainLog(id).toBuilder().geohash("u1hcy").build());

        assertThat(savedEntity().getGeohash()).isEqualTo("u1hcy");
    }

    @Test
    void save_createsNewRowWhenLogIsUnknown() {
        UUID id = UUID.randomUUID();
        when(jpaRepository.findById(id)).thenReturn(Optional.empty());

        EvLog saved = repository.save(domainLog(id));

        assertThat(saved.getId()).isEqualTo(id);
        assertThat(savedEntity().getPowerCurvePoints()).isNull();
    }

    /** Zeile wie sie in der DB liegt: mit Kurve und Telemetrie-Extras. */
    private EvLogEntity persistedRow(UUID id) {
        EvLogEntity entity = new EvLogEntity();
        entity.setId(id);
        entity.setCarId(UUID.randomUUID());
        entity.setDataSource(DataSource.TESLA_LIVE.name());
        entity.setMeasurementType(EnergyMeasurementType.AT_VEHICLE.name());
        entity.setLoggedAt(LocalDateTime.of(2026, 8, 2, 16, 2));
        entity.setPowerCurvePoints(CURVE_JSON);
        entity.setTelemetryExtras(EXTRAS_JSON);
        return entity;
    }

    private EvLog domainLog(UUID id) {
        return EvLog.builder()
                .id(id)
                .carId(UUID.randomUUID())
                .dataSource(DataSource.TESLA_LIVE)
                .measurementType(EnergyMeasurementType.AT_VEHICLE)
                .loggedAt(LocalDateTime.of(2026, 8, 2, 16, 2))
                .build();
    }

    private EvLogEntity savedEntity() {
        ArgumentCaptor<EvLogEntity> captor = ArgumentCaptor.forClass(EvLogEntity.class);
        verify(jpaRepository).save(captor.capture());
        return captor.getValue();
    }
}
