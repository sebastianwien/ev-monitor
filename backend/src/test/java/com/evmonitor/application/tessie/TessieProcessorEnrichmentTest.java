package com.evmonitor.application.tessie;

import com.evmonitor.application.EvLogSavedEvent;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.CarRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The Tessie import writes ev_log rows straight through JdbcTemplate, bypassing EvLogService.
 * That is deliberate (bulk insert), but it means it must publish {@link EvLogSavedEvent} itself -
 * otherwise the weather enrichment never runs and every imported charge stays without a temperature.
 */
class TessieProcessorEnrichmentTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID CAR_ID = UUID.randomUUID();
    private static final String VIN = "5YJ3E7EAXKF000001";
    private static final long STARTED_AT = 1700000000L;

    private NamedParameterJdbcTemplate jdbc;
    private JdbcTemplate innerJdbc;
    private ApplicationEventPublisher eventPublisher;
    private TessieProcessorService processor;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        innerJdbc = mock(JdbcTemplate.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        CarRepository carRepository = mock(CarRepository.class);

        when(jdbc.getJdbcTemplate()).thenReturn(innerJdbc);

        Car car = Car.createNew(USER_ID, CarBrand.CarModel.MODEL_3, 2023, "TST-1", "Standard",
                new BigDecimal("75.0"), new BigDecimal("275.0"), null);
        when(carRepository.findById(CAR_ID)).thenReturn(Optional.of(car));

        processor = new TessieProcessorService(
                jdbc, carRepository, eventPublisher,
                new ClassPathResource("sql/tessie/process_charges_merge.sql"),
                new ClassPathResource("sql/tessie/process_drives_merge.sql"),
                new ClassPathResource("sql/tessie/route_type_backfill.sql"));
    }

    /** One public DC charge in Berlin; drives return nothing. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubOneCharge(int rowsInserted) {
        TessieProcessorService.MergedCharge charge = new TessieProcessorService.MergedCharge(
                STARTED_AT, STARTED_AT + 1800,
                new BigDecimal("40.0"), 20, 75,
                new BigDecimal("26829"), new BigDecimal("52.5"), new BigDecimal("13.4"),
                true, false, new BigDecimal("150"));

        when(jdbc.query(contains("'charge'"), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn((List) List.of(charge));
        when(jdbc.query(contains("'drive'"), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn((List) List.of());
        when(innerJdbc.batchUpdate(anyString(), anyList())).thenReturn(new int[]{rowsInserted});
    }

    @Test
    void publishesEvLogSavedEventSoTheImportedChargeGetsATemperature() {
        stubOneCharge(1);

        processor.processForCar(USER_ID, VIN, CAR_ID);

        ArgumentCaptor<EvLogSavedEvent> captor = ArgumentCaptor.forClass(EvLogSavedEvent.class);
        verify(eventPublisher, times(1)).publishEvent(captor.capture());

        EvLogSavedEvent event = captor.getValue();
        assertNotNull(event.logId());
        assertNotNull(event.geohash(), "Without a geohash the weather enrichment bails out");
        assertEquals(7, event.geohash().length(), "DC charge is public - 7 chars (~150m)");
        assertEquals(LocalDateTime.ofEpochSecond(STARTED_AT, 0,
                        java.time.ZoneId.systemDefault().getRules().getOffset(java.time.Instant.ofEpochSecond(STARTED_AT))),
                event.loggedAt(), "Enrichment looks up the weather at the time of the charge");
        assertNull(event.temperatureCelsius(), "Tessie charges carry no temperature - that is the point");
    }

    @Test
    void publishesNothingWhenTheRowWasADuplicate() {
        // ON CONFLICT DO NOTHING -> batchUpdate reports 0 affected rows
        stubOneCharge(0);

        processor.processForCar(USER_ID, VIN, CAR_ID);

        verify(eventPublisher, never()).publishEvent(any(EvLogSavedEvent.class));
    }
}
