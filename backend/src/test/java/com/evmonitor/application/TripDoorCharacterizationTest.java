package com.evmonitor.application;

import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.DataSource;
import com.evmonitor.domain.EvTrip;
import com.evmonitor.domain.EvTripRepository;
import com.evmonitor.domain.User;
import com.evmonitor.domain.xpeng.DetectedTrip;
import com.evmonitor.domain.xpeng.XpengTripDeduplicator;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Charakterisierung der Trip-Tür ({@link TripService#saveTrip}) und ihrer Dedup- und Löschpfade
 * vor dem Umbau auf ein gemeinsames IngestGateway (Herstellerarchitektur R2/R5). Beschreibt das
 * heutige Verhalten inklusive bekannter Lücken; wer eine Regel bewusst ändert, passt den Test an.
 */
class TripDoorCharacterizationTest extends AbstractIntegrationTest {

    /** Wie {@code XpengImportService.TRIP_DEDUP_WINDOW}. */
    private static final Duration XPENG_GUARD_WINDOW = Duration.ofMinutes(15);
    private static final OffsetDateTime START = OffsetDateTime.of(2026, 9, 10, 8, 0, 0, 0, ZoneOffset.UTC);

    @Autowired
    private TripService tripService;

    @Autowired
    private EvTripRepository tripRepository;

    private User user;
    private Car car;

    @BeforeEach
    void setUp() {
        user = createAndSaveUser("ch-trip-" + UUID.randomUUID().toString().substring(0, 8) + "@t.de");
        car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);
    }

    @Test
    void xpengOverlapGuard_recognisesLiveTrip() {
        tripService.saveTrip(trip(UUID.randomUUID(), user.getId(), DataSource.XPENG_IMPORT, START));

        assertThat(xpengGuardSeesDuplicate(START.plusSeconds(20))).isTrue();
    }

    /**
     * Bekannte Lücke (Plan 2.12): die Kandidaten-Query filtert {@code deletedAt IS NULL}, der Guard
     * sieht den Tombstone nicht. Heute harmlos, weil {@code findByExternalId} vorher greift; nach
     * einem Detektorwechsel mit verschobener Startsekunde (neue externalId) würde die gelöschte
     * Fahrt neu angelegt. Wird in R5 (Trip-Dedup inkl. Tombstones) umgedreht.
     */
    @Test
    void xpengOverlapGuard_doesNotSeeTombstone() {
        UUID id = tripService.saveTrip(trip(UUID.randomUUID(), user.getId(), DataSource.XPENG_IMPORT, START));
        softDelete(id);

        assertThat(xpengGuardSeesDuplicate(START.plusSeconds(20))).isFalse();
    }

    /** Quelle zurücksetzen: hart, inklusive Tombstones; andere Quelle und anderer Nutzer bleiben. */
    @Test
    void resetSource_deletesTombstonesToo_andKeepsEverythingElse() {
        UUID externalId = UUID.randomUUID();
        UUID deleted = tripService.saveTrip(trip(externalId, user.getId(), DataSource.XPENG_IMPORT, START));
        softDelete(deleted);
        tripService.saveTrip(trip(UUID.randomUUID(), user.getId(), DataSource.XPENG_IMPORT, START.plusHours(2)));
        UUID otherSource = tripService.saveTrip(trip(UUID.randomUUID(), user.getId(), DataSource.SMARTCAR_LIVE, START.plusHours(4)));
        User otherUser = createAndSaveUser("ch-trip-other-" + UUID.randomUUID().toString().substring(0, 8) + "@t.de");
        Car otherCar = createAndSaveCar(otherUser.getId(), CarBrand.CarModel.MODEL_3);
        UUID otherUsersTrip = tripService.saveTrip(new InternalTripRequest(UUID.randomUUID(), otherCar.getId(), otherUser.getId(),
                DataSource.XPENG_IMPORT.name(), START, START.plusMinutes(30), null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null));

        int removed = tripRepository.deleteAllByUserIdAndDataSource(user.getId(), DataSource.XPENG_IMPORT.name());

        assertThat(removed).isEqualTo(2);
        assertThat(tripRepository.findById(deleted)).isEmpty();
        assertThat(tripRepository.findById(otherSource)).isPresent();
        assertThat(tripRepository.findById(otherUsersTrip)).isPresent();

        UUID reimported = tripService.saveTrip(trip(externalId, user.getId(), DataSource.XPENG_IMPORT, START));
        assertThat(reimported).isNotEqualTo(deleted);
        assertThat(tripRepository.findById(reimported).orElseThrow().getDeletedAt()).isNull();
    }

    /**
     * Befund Inventur 9.7: {@code saveTrip} prüft nicht, ob das Auto dem Nutzer gehört. Geschützt
     * ist die Tür heute nur durch das Internal-Token (und XPeng ruft sie mit geprüftem Auto auf).
     * Das IngestGateway (R2) prüft Ownership; dann wird dieser Test umgedreht.
     */
    @Test
    void saveTrip_todayAcceptsForeignCar_noOwnershipCheck() {
        User stranger = createAndSaveUser("ch-trip-stranger-" + UUID.randomUUID().toString().substring(0, 8) + "@t.de");

        UUID id = tripService.saveTrip(trip(UUID.randomUUID(), stranger.getId(), DataSource.SMARTCAR_LIVE, START));

        EvTrip saved = tripRepository.findById(id).orElseThrow();
        assertThat(saved.getCarId()).isEqualTo(car.getId());
        assertThat(saved.getUserId()).isEqualTo(stranger.getId());
    }

    /** Nachbau von {@code XpengImportService.isTripAlreadyImported} (privat): Query plus Deduplicator. */
    private boolean xpengGuardSeesDuplicate(OffsetDateTime candidateStart) {
        List<EvTrip> candidates = tripRepository.findByCarIdAndTripStartedAtBetweenOrderByTripStartedAtAsc(
                car.getId(), candidateStart.minus(XPENG_GUARD_WINDOW), candidateStart.plus(XPENG_GUARD_WINDOW));
        DetectedTrip candidate = new DetectedTrip(candidateStart.toInstant(), candidateStart.plusMinutes(30).toInstant(),
                new BigDecimal("12000.0"), new BigDecimal("12025.0"), new BigDecimal("25.0"),
                null, null, null, null, null, Map.of());
        return XpengTripDeduplicator.isAlreadyImported(candidates, candidate);
    }

    private void softDelete(UUID tripId) {
        EvTrip trip = tripRepository.findById(tripId).orElseThrow();
        trip.setDeletedAt(OffsetDateTime.now());
        tripRepository.save(trip);
    }

    private InternalTripRequest trip(UUID externalId, UUID userId, DataSource source, OffsetDateTime startedAt) {
        return new InternalTripRequest(externalId, car.getId(), userId, source.name(), startedAt, startedAt.plusMinutes(30),
                new BigDecimal("80"), new BigDecimal("70"), new BigDecimal("12000.0"), new BigDecimal("12025.0"),
                new BigDecimal("25.0"), null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
