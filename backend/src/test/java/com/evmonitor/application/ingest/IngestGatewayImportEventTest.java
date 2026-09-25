package com.evmonitor.application.ingest;

import com.evmonitor.application.InternalTripRequest;
import com.evmonitor.application.ingest.event.ImportEventOutcome;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.DataSource;
import com.evmonitor.domain.User;
import com.evmonitor.domain.exception.ForbiddenException;
import com.evmonitor.domain.exception.NotFoundException;
import com.evmonitor.domain.exception.ValidationException;
import com.evmonitor.infrastructure.persistence.ingest.ImportEvent;
import com.evmonitor.infrastructure.persistence.ingest.ImportEventRepository;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Jeder Gateway-Aufruf hinterlässt genau eine Zeile im Import-Protokoll - auch wenn er abgelehnt wird.
 * Das Verhalten nach außen (Rückgabe, Exceptions) bleibt dabei unverändert.
 */
class IngestGatewayImportEventTest extends AbstractIntegrationTest {

    @Autowired IngestGateway gateway;
    @Autowired ImportEventRepository importEventRepository;

    private User user;
    private Car car;

    @BeforeEach
    void setUp() {
        user = createAndSaveUser("ingest-event-" + UUID.randomUUID().toString().substring(0, 8) + "@t.de");
        car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);
    }

    @AfterEach
    void cleanUp() {
        importEventRepository.deleteByUserId(user.getId());
    }

    @Test
    void batch_recordsImportedWithCountsAndOrigin() {
        gateway.ingestCharging(batch(user.getId(), car.getId(), entry(10), entry(11)));

        assertThat(events()).singleElement().satisfies(e -> {
            assertThat(e.getOutcome()).isEqualTo(ImportEventOutcome.IMPORTED);
            assertThat(e.getProvider()).isEqualTo("VW_GROUP");
            assertThat(e.getChannel()).isEqualTo("UPLOAD");
            assertThat(e.getDataSource()).isEqualTo("EU_DATA_ACT_IMPORT");
            assertThat(e.getUserId()).isEqualTo(user.getId());
            assertThat(e.getCarId()).isEqualTo(car.getId());
            assertThat(e.getSessionsImported()).isEqualTo(2);
            assertThat(e.getSessionsSkipped()).isZero();
            assertThat(e.getDurationMs()).isNotNull().isNotNegative();
            assertThat(e.getError()).isNull();
        });
    }

    @Test
    void sameBatchAgain_recordsNoNewData() {
        gateway.ingestCharging(batch(user.getId(), car.getId(), entry(10)));
        gateway.ingestCharging(batch(user.getId(), car.getId(), entry(10)));

        assertThat(events()).extracting(ImportEvent::getOutcome)
                .containsExactly(ImportEventOutcome.IMPORTED, ImportEventOutcome.NO_NEW_DATA);
        assertThat(events().get(1).getSessionsSkipped()).isEqualTo(1);
    }

    @Test
    void foreignCar_recordsRejected_andStillThrows() {
        User stranger = createAndSaveUser("ingest-event-x-" + UUID.randomUUID().toString().substring(0, 8) + "@t.de");
        try {
            assertThatThrownBy(() -> gateway.ingestCharging(batch(stranger.getId(), car.getId(), entry(10))))
                    .isInstanceOf(ForbiddenException.class);

            assertThat(eventsOf(stranger.getId())).singleElement().satisfies(e -> {
                assertThat(e.getOutcome()).isEqualTo(ImportEventOutcome.REJECTED);
                assertThat(e.getCarId()).isEqualTo(car.getId());
                assertThat(e.getError()).startsWith("ForbiddenException");
            });
        } finally {
            importEventRepository.deleteByUserId(stranger.getId());
        }
    }

    @Test
    void unknownCar_recordsRejectedWithoutCarId() {
        assertThatThrownBy(() -> gateway.ingestCharging(batch(user.getId(), UUID.randomUUID(), entry(10))))
                .isInstanceOf(NotFoundException.class);

        assertThat(events()).singleElement().satisfies(e -> {
            assertThat(e.getOutcome()).isEqualTo(ImportEventOutcome.REJECTED);
            assertThat(e.getCarId()).isNull();
        });
    }

    @Test
    void connectorPush_recordsLiveChannel() {
        gateway.ingestCharging(new IngestCommand(user.getId(), car.getId(), DataSource.SMARTCAR_LIVE,
                IngestDoor.CONNECTOR_PUSH, List.of(entry(9))));

        assertThat(events()).singleElement().satisfies(e -> {
            assertThat(e.getProvider()).isEqualTo("SMARTCAR");
            assertThat(e.getChannel()).isEqualTo("LIVE");
            assertThat(e.getSessionsImported()).isEqualTo(1);
        });
    }

    @Test
    void trip_newThenExisting() {
        UUID externalId = UUID.randomUUID();
        gateway.ingestTrip(trip(externalId, "XPENG_IMPORT"));
        gateway.ingestTrip(trip(externalId, "XPENG_IMPORT"));

        List<ImportEvent> events = events();
        assertThat(events).extracting(ImportEvent::getOutcome)
                .containsExactly(ImportEventOutcome.IMPORTED, ImportEventOutcome.NO_NEW_DATA);
        assertThat(events.get(0).getTripsImported()).isEqualTo(1);
        assertThat(events.get(0).getProvider()).isEqualTo("XPENG");
        assertThat(events.get(1).getTripsSkipped()).isEqualTo(1);
    }

    @Test
    void trip_unknownSource_isStillRecorded() {
        gateway.ingestTrip(trip(UUID.randomUUID(), "SOMETHING_NEW"));

        assertThat(events()).singleElement().satisfies(e -> {
            assertThat(e.getProvider()).isEqualTo(ImportEvent.UNKNOWN);
            assertThat(e.getDataSource()).isEqualTo("SOMETHING_NEW");
        });
    }

    @Test
    void trip_withoutUser_isRejected() {
        InternalTripRequest withoutUser = trip(UUID.randomUUID(), "TESLA_LIVE", null);

        assertThatThrownBy(() -> gateway.ingestTrip(withoutUser)).isInstanceOf(ValidationException.class);

        assertThat(importEventRepository.findAll()).filteredOn(e -> car.getId().equals(e.getCarId()))
                .singleElement().satisfies(e -> {
                    assertThat(e.getOutcome()).isEqualTo(ImportEventOutcome.REJECTED);
                    assertThat(e.getUserId()).isNull();
                });
        importEventRepository.deleteAll(importEventRepository.findAll().stream()
                .filter(e -> car.getId().equals(e.getCarId())).toList());
    }

    @Test
    void trip_foreignCar_recordsRejected() {
        User stranger = createAndSaveUser("ev-stranger-" + UUID.randomUUID().toString().substring(0, 8) + "@t.de");

        assertThatThrownBy(() -> gateway.ingestTrip(trip(UUID.randomUUID(), "SMARTCAR_LIVE", stranger.getId())))
                .isInstanceOf(ForbiddenException.class);

        assertThat(eventsOf(stranger.getId())).singleElement().satisfies(e -> {
            assertThat(e.getOutcome()).isEqualTo(ImportEventOutcome.REJECTED);
            assertThat(e.getTripsImported()).isZero();
        });
        importEventRepository.deleteByUserId(stranger.getId());
    }

    private List<ImportEvent> events() {
        return eventsOf(user.getId());
    }

    private List<ImportEvent> eventsOf(UUID userId) {
        return importEventRepository.findAll().stream()
                .filter(e -> userId.equals(e.getUserId()))
                .sorted(Comparator.comparing(ImportEvent::getCreatedAt))
                .toList();
    }

    private static IngestCommand batch(UUID userId, UUID carId, ChargingEntry... entries) {
        return new IngestCommand(userId, carId, DataSource.EU_DATA_ACT_IMPORT, IngestDoor.IMPORT_BATCH, List.of(entries));
    }

    private static ChargingEntry entry(int hour) {
        return ChargingEntry.builder()
                .loggedAt(LocalDateTime.of(2026, 9, 10, hour, 0))
                .kwhCharged(new BigDecimal("20.0"))
                .build();
    }

    private InternalTripRequest trip(UUID externalId, String dataSource) {
        return trip(externalId, dataSource, user.getId());
    }

    private InternalTripRequest trip(UUID externalId, String dataSource, UUID userId) {
        OffsetDateTime start = OffsetDateTime.of(2026, 9, 10, 8, 0, 0, 0, ZoneOffset.UTC);
        return InternalTripRequest.builder()
                .externalId(externalId)
                .carId(car.getId())
                .userId(userId)
                .dataSource(dataSource)
                .tripStartedAt(start)
                .tripEndedAt(start.plusMinutes(30))
                .distanceKm(new BigDecimal("12.0"))
                .build();
    }
}
