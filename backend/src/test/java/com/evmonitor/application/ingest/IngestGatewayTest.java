package com.evmonitor.application.ingest;

import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.DataSource;
import com.evmonitor.domain.User;
import com.evmonitor.domain.exception.ForbiddenException;
import com.evmonitor.domain.exception.NotFoundException;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Das Gateway selbst: Ownership vor jedem Schreibzugriff und Zählung je Eintrag. Die Regeln der
 * einzelnen Türen halten die Charakterisierungstests der alten Einstiege fest.
 */
class IngestGatewayTest extends AbstractIntegrationTest {

    @Autowired
    private IngestGateway gateway;

    private User user;
    private Car car;

    @BeforeEach
    void setUp() {
        user = createAndSaveUser("ingest-" + UUID.randomUUID().toString().substring(0, 8) + "@t.de");
        car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);
    }

    @Test
    void foreignCar_isRejectedBeforeAnyWrite() {
        User stranger = createAndSaveUser("ingest-stranger-" + UUID.randomUUID().toString().substring(0, 8) + "@t.de");

        assertThatThrownBy(() -> gateway.ingestCharging(command(stranger.getId(), car.getId(), entry(10), entry(11))))
                .isInstanceOf(ForbiddenException.class);
        assertThat(evLogRepository.findAllByCarId(car.getId())).isEmpty();
    }

    @Test
    void unknownCar_isNotFound() {
        assertThatThrownBy(() -> gateway.ingestCharging(command(user.getId(), UUID.randomUUID(), entry(10))))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void duplicateInBatch_isCountedAsSkipped_andCreatedLogsAreReturned() {
        IngestResult result = gateway.ingestCharging(command(user.getId(), car.getId(), entry(10), entry(11)));
        IngestResult again = gateway.ingestCharging(command(user.getId(), car.getId(), entry(11), entry(12)));

        assertThat(result.imported()).isEqualTo(2);
        assertThat(result.created()).hasSize(2);
        assertThat(again.imported()).isEqualTo(1);
        assertThat(again.skipped()).isEqualTo(1);
        assertThat(evLogRepository.findAllByCarId(car.getId())).hasSize(3);
    }

    private static IngestCommand command(UUID userId, UUID carId, ChargingEntry... entries) {
        return new IngestCommand(userId, carId, DataSource.SMARTCAR_LIVE, IngestDoor.CONNECTOR_PUSH, List.of(entries));
    }

    private static ChargingEntry entry(int hour) {
        return ChargingEntry.builder()
                .loggedAt(LocalDateTime.of(2026, 9, 10, hour, 0))
                .kwhCharged(new BigDecimal("20.0"))
                .build();
    }
}
