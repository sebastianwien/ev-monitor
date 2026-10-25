package com.evmonitor.application;

import com.evmonitor.application.ChargingPlaceResponse.Kind;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.ChargingType;
import com.evmonitor.domain.DataSource;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.EvLogRepository;
import com.evmonitor.domain.User;
import com.evmonitor.domain.UserRepository;
import com.evmonitor.domain.exception.NotFoundException;
import com.evmonitor.infrastructure.persistence.ChargingSavingsQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Ladeorte der Story-Karte "Wo du lädst": je gespeichertem Geohash eine Zeile mit Menge, Geld und
 * Preis je kWh. Es zählen dieselben Ladungen wie in der Statistik (Kosten-Kachel), sonst passt die
 * Summe der Orte nicht zu den Stromkosten des Monats.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChargingPlacesServiceTest {

    private static final String HOME = "u1x0yz";
    private static final String WORK = "u1x0ab";
    private static final String SUPERCHARGER = "u1x3k7q";
    private static final String ROADSIDE = "u1x9pp2";

    @Mock CarRepository carRepository;
    @Mock UserRepository userRepository;
    @Mock EvLogRepository evLogRepository;
    @Mock ChargingSavingsQueryRepository savingsQueryRepository;
    @InjectMocks ChargingPlacesService service;

    private final UUID carId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private User user;

    @BeforeEach
    void setUp() {
        Car car = mock(Car.class);
        when(car.isOwnedBy(userId)).thenReturn(true);
        when(carRepository.findById(carId)).thenReturn(Optional.of(car));
        user = mock(User.class);
        when(user.isSeedData()).thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(savingsQueryRepository.homeGeohash(userId)).thenReturn(HOME);
    }

    @Test
    void groupsByGeohashWithSumsAndNewestCharge() {
        EvLog older = log(SUPERCHARGER, "2026-08-02T10:00").kwhCharged(bd("40")).costEur(bd("16.00")).build();
        EvLog newer = log(SUPERCHARGER, "2026-08-28T10:00").kwhCharged(bd("60")).costEur(bd("24.00")).build();
        logs(older, newer, log(HOME, "2026-08-10T20:00").publicCharging(false).kwhCharged(bd("30")).costEur(bd("9.00")).build());

        Map<String, ChargingPlaceResponse> places = places(null, null);

        ChargingPlaceResponse sc = places.get(SUPERCHARGER);
        assertEquals(2, sc.charges());
        assertEquals(0, bd("100").compareTo(sc.kwh()));
        assertEquals(0, bd("40.00").compareTo(sc.costEur()));
        assertEquals(0, bd("0.400").compareTo(sc.pricePerKwh()));
        assertEquals(LocalDateTime.parse("2026-08-28T10:00"), sc.lastChargedAt());
        assertEquals(newer.getId(), sc.lastLogId());
        assertEquals(2, places.size());
    }

    @Test
    void kindFollowsHomeGeohashAndPublicFlag() {
        logs(log(HOME, "2026-08-01T20:00").publicCharging(false).build(),
                log(WORK, "2026-08-02T09:00").publicCharging(false).build(),
                log(SUPERCHARGER, "2026-08-03T10:00").publicCharging(true).build(),
                log(ROADSIDE, "2026-08-04T10:00").publicCharging(null).build());

        Map<String, ChargingPlaceResponse> places = places(null, null);

        assertEquals(Kind.HOME, places.get(HOME).kind());
        assertEquals(Kind.PRIVATE, places.get(WORK).kind());
        assertEquals(Kind.PUBLIC, places.get(SUPERCHARGER).kind());
        assertEquals(Kind.UNKNOWN, places.get(ROADSIDE).kind());
    }

    @Test
    void confirmedPublicWinsOverUnknownAtTheSamePlace() {
        logs(log(ROADSIDE, "2026-08-01T10:00").publicCharging(null).build(),
                log(ROADSIDE, "2026-08-02T10:00").publicCharging(true).build());

        assertEquals(Kind.PUBLIC, places(null, null).get(ROADSIDE).kind());
    }

    @Test
    void priceCountsOnlyPaidChargesWithEnergy() {
        // Kosten 0 zählt bei Ladungen und kWh, nicht beim Preis. Kosten ohne kWh verzerren den Preis nicht.
        logs(log(SUPERCHARGER, "2026-08-01T10:00").kwhCharged(bd("50")).costEur(bd("20.00")).build(),
                log(SUPERCHARGER, "2026-08-02T10:00").kwhCharged(bd("30")).costEur(BigDecimal.ZERO).build(),
                log(SUPERCHARGER, "2026-08-03T10:00").kwhCharged(null).costEur(bd("5.00")).build(),
                log(SUPERCHARGER, "2026-08-04T10:00").kwhCharged(bd("20")).costEur(null).build());

        ChargingPlaceResponse sc = places(null, null).get(SUPERCHARGER);

        assertEquals(4, sc.charges());
        assertEquals(0, bd("100").compareTo(sc.kwh()));
        assertEquals(0, bd("25.00").compareTo(sc.costEur()));
        assertEquals(0, bd("0.400").compareTo(sc.pricePerKwh()));
    }

    @Test
    void vehicleSideEnergyCountsWhenChargerEnergyIsMissing() {
        logs(log(SUPERCHARGER, "2026-08-01T10:00").dataSource(DataSource.TESLA_LIVE)
                .kwhCharged(null).kwhAtVehicle(bd("40")).costEur(bd("18.00")).build());

        ChargingPlaceResponse sc = places(null, null).get(SUPERCHARGER);

        assertEquals(0, bd("40").compareTo(sc.kwh()));
        assertEquals(0, bd("0.450").compareTo(sc.pricePerKwh()));
    }

    @Test
    void withoutPaidChargeThereIsNoPrice() {
        logs(log(HOME, "2026-08-01T20:00").publicCharging(false).costEur(null).build());

        assertNull(places(null, null).get(HOME).pricePerKwh());
    }

    @Test
    void mostFrequentOperatorAndItsChargingType() {
        logs(log(SUPERCHARGER, "2026-08-01T10:00").cpoName("Tesla Supercharger Hamburg").chargingType(ChargingType.DC).build(),
                log(SUPERCHARGER, "2026-08-02T10:00").cpoName("Tesla Supercharger Hamburg").chargingType(ChargingType.DC).build(),
                log(SUPERCHARGER, "2026-08-03T10:00").cpoName("EnBW").chargingType(ChargingType.AC).build(),
                log(SUPERCHARGER, "2026-08-04T10:00").cpoName(" ").chargingType(ChargingType.AC).build(),
                log(SUPERCHARGER, "2026-08-05T10:00").cpoName(null).chargingType(ChargingType.AC).build());

        ChargingPlaceResponse sc = places(null, null).get(SUPERCHARGER);

        assertEquals("Tesla Supercharger Hamburg", sc.cpoName());
        assertEquals(ChargingType.DC, sc.chargingType());
    }

    @Test
    void withoutOperatorTheTypeComesFromAllChargesAtThePlace() {
        logs(log(ROADSIDE, "2026-08-01T10:00").chargingType(ChargingType.AC).build(),
                log(ROADSIDE, "2026-08-02T10:00").chargingType(ChargingType.AC).build(),
                log(ROADSIDE, "2026-08-03T10:00").chargingType(ChargingType.DC).build());

        ChargingPlaceResponse place = places(null, null).get(ROADSIDE);

        assertNull(place.cpoName());
        assertEquals(ChargingType.AC, place.chargingType());
    }

    @Test
    void periodIsInclusiveOnBothDays() {
        logs(log(SUPERCHARGER, "2026-07-31T23:59").build(),
                log(SUPERCHARGER, "2026-08-01T00:00").build(),
                log(SUPERCHARGER, "2026-08-31T23:59").build(),
                log(SUPERCHARGER, "2026-09-01T00:00").build());

        assertEquals(2, places(LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-31")).get(SUPERCHARGER).charges());
    }

    @Test
    void sameFilterAsStatistics_excludedLogsAndLogsWithoutPlaceDoNotCount() {
        logs(log(SUPERCHARGER, "2026-08-01T10:00").costEur(bd("10.00")).build(),
                log(SUPERCHARGER, "2026-08-02T10:00").costEur(bd("99.00")).includeInStatistics(false).build(),
                log(null, "2026-08-03T10:00").costEur(bd("7.00")).build());

        Map<String, ChargingPlaceResponse> places = places(null, null);

        assertEquals(1, places.size());
        assertEquals(0, bd("10.00").compareTo(places.get(SUPERCHARGER).costEur()));
    }

    @Test
    void seedUserSeesExcludedLogsLikeInStatistics() {
        when(user.isSeedData()).thenReturn(true);
        logs(log(SUPERCHARGER, "2026-08-02T10:00").includeInStatistics(false).build());

        assertEquals(1, places(null, null).get(SUPERCHARGER).charges());
    }

    @Test
    void sortedByMoneyDescending() {
        logs(log(HOME, "2026-08-01T20:00").publicCharging(false).costEur(bd("5.00")).build(),
                log(SUPERCHARGER, "2026-08-02T10:00").costEur(bd("30.00")).build(),
                log(ROADSIDE, "2026-08-03T10:00").costEur(bd("12.00")).build());

        List<ChargingPlaceResponse> places = service.getPlaces(carId, userId, null, null);

        assertEquals(List.of(SUPERCHARGER, ROADSIDE, HOME), places.stream().map(ChargingPlaceResponse::geohash).toList());
    }

    @Test
    void foreignCarIsNotFound() {
        UUID stranger = UUID.randomUUID();

        assertThrows(NotFoundException.class, () -> service.getPlaces(carId, stranger, null, null));
    }

    private void logs(EvLog... logs) {
        when(evLogRepository.findAllByCarId(carId)).thenReturn(List.of(logs));
    }

    private Map<String, ChargingPlaceResponse> places(LocalDate from, LocalDate to) {
        return service.getPlaces(carId, userId, from, to).stream()
                .collect(Collectors.toMap(ChargingPlaceResponse::geohash, Function.identity()));
    }

    private EvLog.EvLogBuilder log(String geohash, String loggedAt) {
        LocalDateTime at = LocalDateTime.parse(loggedAt);
        return EvLog.builder()
                .id(UUID.randomUUID())
                .carId(carId)
                .geohash(geohash)
                .kwhCharged(bd("20"))
                .costEur(bd("8.00"))
                .publicCharging(true)
                .chargingType(ChargingType.DC)
                .loggedAt(at)
                .includeInStatistics(true)
                .createdAt(at)
                .updatedAt(at);
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }
}
