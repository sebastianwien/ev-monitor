package com.evmonitor.application.ingest;

import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.DataSource;
import com.evmonitor.domain.EvTrip;
import com.evmonitor.domain.EvTripRepository;
import com.evmonitor.domain.RouteType;
import com.evmonitor.domain.RouteTypeSource;
import com.evmonitor.domain.TireType;
import com.evmonitor.domain.User;
import com.evmonitor.domain.exception.ForbiddenException;
import com.evmonitor.domain.exception.NotFoundException;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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

    @Autowired
    private EvTripRepository evTripRepository;

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

    @Test
    void reuploadOfDeletedLog_isSkipped_andReportedAsDeleted() {
        IngestResult first = gateway.ingestCharging(command(user.getId(), car.getId(), entry(10), entry(11)));
        evLogRepository.softDelete(first.created().get(0).getId());

        IngestResult again = gateway.ingestCharging(command(user.getId(), car.getId(), entry(10), entry(11)));

        assertThat(again.imported()).isZero();
        assertThat(again.skipped()).isEqualTo(2);
        assertThat(again.skippedDeleted()).isEqualTo(1);
    }

    // ── Ueberschneidungsregel fuer VW-Drops (EU_DATA_ACT_SYNC): ein Auto laedt nicht zweimal gleichzeitig ──

    @Test
    void vwDropCharge_overlappingUserLoggedCharge_isSkipped_otherSourcesUnchanged() {
        // Nutzer hat 10:00 bis 11:00 geloggt (ID.3b: Nutzer loggt manuell, VW liefert dieselbe Ladung mit geschaetztem Start)
        gateway.ingestCharging(command(user.getId(), car.getId(), DataSource.USER_LOGGED, entry(10, 60)));

        IngestResult vw = gateway.ingestCharging(command(user.getId(), car.getId(), DataSource.EU_DATA_ACT_SYNC, entry(9, 90)));
        IngestResult smartcar = gateway.ingestCharging(command(user.getId(), car.getId(), DataSource.SMARTCAR_LIVE, entry(9, 90)));

        assertThat(vw.imported()).isZero();
        assertThat(vw.skipped()).isEqualTo(1);
        assertThat(vw.skippedDeleted()).isZero();
        assertThat(smartcar.imported()).isEqualTo(1);
        assertThat(evLogRepository.findAllByCarId(car.getId())).hasSize(2);
    }

    @Test
    void vwDropCharge_touchingOnlyAtBoundary_orDisjoint_isCreated() {
        gateway.ingestCharging(command(user.getId(), car.getId(), DataSource.USER_LOGGED, entry(10, 60)));

        IngestResult after = gateway.ingestCharging(command(user.getId(), car.getId(), DataSource.EU_DATA_ACT_SYNC, entry(12, 30)));
        IngestResult before = gateway.ingestCharging(command(user.getId(), car.getId(), DataSource.EU_DATA_ACT_SYNC, entry(7, 60)));

        assertThat(after.imported()).isEqualTo(1);
        assertThat(before.imported()).isEqualTo(1);
    }

    @Test
    void vwDropCharge_overlappingDeletedCharge_staysDeleted() {
        IngestResult first = gateway.ingestCharging(command(user.getId(), car.getId(), DataSource.SMARTCAR_LIVE, entry(10, 60)));
        evLogRepository.softDelete(first.created().get(0).getId());

        IngestResult vw = gateway.ingestCharging(command(user.getId(), car.getId(), DataSource.EU_DATA_ACT_SYNC, entry(10, 45)));

        assertThat(vw.imported()).isZero();
        assertThat(evLogRepository.findAllByCarId(car.getId())).isEmpty();
    }

    @Test
    void vwDropCharge_withoutDuration_overlapsAsPointInTime() {
        gateway.ingestCharging(command(user.getId(), car.getId(), DataSource.USER_LOGGED, entry(10, 60)));

        IngestResult inside = gateway.ingestCharging(command(user.getId(), car.getId(), DataSource.EU_DATA_ACT_SYNC, entry(10, 0)));
        IngestResult outside = gateway.ingestCharging(command(user.getId(), car.getId(), DataSource.EU_DATA_ACT_SYNC, entry(13, 0)));

        assertThat(inside.imported()).isZero();
        assertThat(outside.imported()).isEqualTo(1);
    }

    @Test
    void vwDropCharge_rawImportDataIsStored() {
        IngestResult r = gateway.ingestCharging(command(user.getId(), car.getId(), DataSource.EU_DATA_ACT_SYNC,
                ChargingEntry.builder().loggedAt(LocalDateTime.of(2026, 9, 10, 20, 0)).kwhCharged(new BigDecimal("16.5"))
                        .chargeDurationMinutes(114).rawImportData("{\"startEstimated\":true,\"startMethod\":\"ENERGY_OVER_POWER\"}").build()));

        assertThat(r.imported()).isEqualTo(1);
        assertThat(evLogRepository.findById(r.created().get(0).getId()).orElseThrow().getRawImportData()).contains("ENERGY_OVER_POWER");
    }

    @Test
    void liveChargeInheritsTheTireButNotTheRouteType() {
        // a charge with a route type the user chose, then a live charge without one
        gateway.ingestCharging(command(user.getId(), car.getId(), ChargingEntry.builder()
                .loggedAt(LocalDateTime.of(2026, 9, 10, 10, 0)).kwhCharged(new BigDecimal("20.0"))
                .routeType(RouteType.HIGHWAY).tireType(TireType.WINTER).build()));

        IngestResult live = gateway.ingestCharging(command(user.getId(), car.getId(), entry(14)));

        var log = evLogRepository.findById(live.created().get(0).getId()).orElseThrow();
        // tires stay on the car; the route of the next trip is unknown, not the one before
        assertThat(log.getTireType()).isEqualTo(TireType.WINTER);
        assertThat(log.getRouteType()).isNull();
    }

    // ── Streckentyp wird beim Eingang abgeleitet (Schritt 4a) ──

    @Test
    void wallboxCharge_getsRouteTypeFromTripsOfTheSameCar_afterIngest() {
        gateway.ingestCharging(command(user.getId(), car.getId(), DataSource.WALLBOX_GOE,
                ChargingEntry.builder().loggedAt(LocalDateTime.of(2026, 3, 1, 7, 0)).kwhCharged(new BigDecimal("30.0"))
                        .chargeDurationMinutes(60).odometerKm(10_000).build()));
        // 150 km in 90 min (Autobahn) und 40 km in 60 min (Stadt): 190 von 200 km abgedeckt
        teslaLiveTrip(OffsetDateTime.of(2026, 3, 1, 9, 0, 0, 0, ZoneOffset.UTC), 90, 150);
        teslaLiveTrip(OffsetDateTime.of(2026, 3, 1, 14, 0, 0, 0, ZoneOffset.UTC), 60, 40);

        IngestResult r = gateway.ingestCharging(command(user.getId(), car.getId(), DataSource.WALLBOX_GOE,
                ChargingEntry.builder().loggedAt(LocalDateTime.of(2026, 3, 1, 20, 0)).kwhCharged(new BigDecimal("30.0"))
                        .chargeDurationMinutes(60).odometerKm(10_200).build()));

        assertThat(r.imported()).isEqualTo(1);
        var log = evLogRepository.findById(r.created().get(0).getId()).orElseThrow();
        assertThat(log.getRouteType()).isEqualTo(RouteType.HIGHWAY);
        assertThat(log.getRouteTypeSource()).isEqualTo(RouteTypeSource.TRIPS);
    }

    @Test
    void firstChargeOfACar_isIngested_withoutRouteType() {
        IngestResult r = gateway.ingestCharging(command(user.getId(), car.getId(), DataSource.WALLBOX_GOE,
                ChargingEntry.builder().loggedAt(LocalDateTime.of(2026, 3, 1, 20, 0)).kwhCharged(new BigDecimal("30.0"))
                        .odometerKm(10_200).build()));

        assertThat(r.imported()).isEqualTo(1);
        assertThat(evLogRepository.findById(r.created().get(0).getId()).orElseThrow().getRouteType()).isNull();
    }

    private void teslaLiveTrip(OffsetDateTime start, int minutes, double km) {
        evTripRepository.save(EvTrip.builder().userId(user.getId()).carId(car.getId()).dataSource(EvTrip.DATA_SOURCE_TESLA_LIVE)
                .tripStartedAt(start).tripEndedAt(start.plusMinutes(minutes)).distanceKm(BigDecimal.valueOf(km))
                .status("COMPLETED").createdAt(OffsetDateTime.now()).build());
    }

    private static IngestCommand command(UUID userId, UUID carId, ChargingEntry... entries) {
        return command(userId, carId, DataSource.SMARTCAR_LIVE, entries);
    }

    private static IngestCommand command(UUID userId, UUID carId, DataSource source, ChargingEntry... entries) {
        return new IngestCommand(userId, carId, source, IngestDoor.CONNECTOR_PUSH, List.of(entries));
    }

    /** Ladung ab {@code hour} Uhr mit Dauer; 0 = ohne Dauer (Zeitpunkt). */
    private static ChargingEntry entry(int hour, int minutes) {
        return ChargingEntry.builder()
                .loggedAt(LocalDateTime.of(2026, 9, 10, hour, 0))
                .kwhCharged(new BigDecimal("20.0"))
                .chargeDurationMinutes(minutes > 0 ? minutes : null)
                .build();
    }

    private static ChargingEntry entry(int hour) {
        return ChargingEntry.builder()
                .loggedAt(LocalDateTime.of(2026, 9, 10, hour, 0))
                .kwhCharged(new BigDecimal("20.0"))
                .build();
    }
}
