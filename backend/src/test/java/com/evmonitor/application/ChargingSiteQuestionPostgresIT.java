package com.evmonitor.application;

import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.ChargingSiteQuestionRepository;
import com.evmonitor.domain.ChargingType;
import com.evmonitor.domain.DataSource;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.User;
import com.evmonitor.domain.UserRepository;
import com.evmonitor.testutil.AbstractPostgresIT;
import com.evmonitor.testutil.TestDataBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Säule und Frage gegen echtes Postgres: H2 kennt weder den Fremdschlüssel von ev_log auf
 * charging_site noch jsonb. Übersprungen ohne Docker.
 */
class ChargingSiteQuestionPostgresIT extends AbstractPostgresIT {

    private static final String CELL = "u0v9zq5";

    @Autowired ChargingSiteAttacher attacher;
    @Autowired ChargingSiteQuestionService questions;
    @Autowired ChargingSiteQuestionRepository questionRepository;
    @Autowired UserRepository userRepository;
    @Autowired CarRepository carRepository;
    @Autowired ObjectMapper objectMapper;

    private User user;
    private Car car;

    @BeforeEach
    void setUp() {
        user = userRepository.save(TestDataBuilder.createTestUser("site-pg-" + UUID.randomUUID().toString().substring(0, 8) + "@t.de"));
        car = carRepository.save(TestDataBuilder.createTestCar(user.getId(), CarBrand.CarModel.MODEL_3, new BigDecimal("75.0")));
    }

    /** Regression: die neue Säule muss in der Datenbank stehen, bevor das Update der Ladung auf sie zeigt. */
    @Test
    void neueSäuleWirdZugeordnetOhneFremdschlüsselFehler() {
        EvLog log = saveLog(LocalDateTime.of(2026, 10, 18, 20, 0));

        boolean attached = attacher.attach(log, user.getId(), station("Neu " + UUID.randomUUID(), 22.0));

        assertThat(attached).isTrue();
        assertThat(evLogRepository.findById(log.getId()).orElseThrow().getChargingSiteId()).isNotNull();
    }

    @Test
    void frageUndAntwortAufPostgres() throws Exception {
        EvLog log = saveLog(LocalDateTime.of(2026, 10, 18, 20, 0));
        String wanted = "Gewählt " + UUID.randomUUID();
        String json = objectMapper.writeValueAsString(List.of(station(wanted, 22.0), station("Andere " + UUID.randomUUID(), 43.0)));
        questionRepository.openIfAbsent(user.getId(), CELL, ChargingType.AC, json);
        questionRepository.openIfAbsent(user.getId(), CELL, ChargingType.AC, json);

        var open = questions.openQuestions(user.getId());
        assertThat(open).hasSize(1);
        assertThat(open.get(0).logCount()).isEqualTo(1);
        assertThat(open.get(0).candidates()).hasSize(2);

        var result = questions.answer(user.getId(), open.get(0).id(), wanted);

        assertThat(result.assigned()).isEqualTo(1);
        assertThat(evLogRepository.findById(log.getId()).orElseThrow().getChargingSiteId()).isNotNull();
        assertThat(questions.openQuestions(user.getId())).isEmpty();
    }

    private EvLog saveLog(LocalDateTime at) {
        EvLog log = EvLog.createNewWithSource(car.getId(), new BigDecimal("20.0"), null, 60, CELL,
                null, null, null, at, DataSource.TESLA_LIVE, ChargingType.AC, null);
        return evLogRepository.save(log.toBuilder().publicCharging(true).maxChargingPowerKw(new BigDecimal("11")).build());
    }

    private static NearbyStation station(String name, double acKw) {
        return new NearbyStation(name, false, 50, acKw, null, 2, CELL,
                null, null, null, "Hauptstraße", "1", "12345", "Beispielstadt", List.of("Typ 2"), null, null, null, null);
    }
}
