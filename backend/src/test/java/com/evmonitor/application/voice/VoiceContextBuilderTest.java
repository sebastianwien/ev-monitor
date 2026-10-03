package com.evmonitor.application.voice;

import com.evmonitor.application.ChargingSiteService;
import com.evmonitor.application.NearbyCpoService;
import com.evmonitor.application.NearbyStation;
import com.evmonitor.application.user.UserChargingProviderResponse;
import com.evmonitor.application.user.UserChargingProviderService;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.ChargingSite;
import com.evmonitor.domain.ChargingSiteSource;
import com.evmonitor.domain.ChargingSiteUsage;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.EvLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class VoiceContextBuilderTest {

    static final UUID USER = UUID.randomUUID();
    static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    /** Samstag 03.10.2026, 23:30 UTC - in Berlin ist es schon Sonntag. */
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-03T23:30:00Z"), ZoneOffset.UTC);

    private final NearbyCpoService nearby = mock(NearbyCpoService.class);
    private final ChargingSiteService sites = mock(ChargingSiteService.class);
    private final UserChargingProviderService providers = mock(UserChargingProviderService.class);
    private final EvLogRepository logs = mock(EvLogRepository.class);
    private final Car car = mock(Car.class);
    private VoiceContextBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new VoiceContextBuilder(nearby, sites, providers, logs, CLOCK);
        when(car.getId()).thenReturn(UUID.randomUUID());
        when(car.getEffectiveBatteryCapacityKwh()).thenReturn(new BigDecimal("73.150"));
        when(sites.recentlyUsed(USER)).thenReturn(List.of());
        when(providers.getAll(USER)).thenReturn(List.of());
        when(logs.findLatestByCarId(car.getId(), VoiceContextBuilder.RECENT_LOGS)).thenReturn(List.of());
    }

    static NearbyStation station(String name, String geohash) {
        return new NearbyStation(name, true, 50, 150.0, true, 4, geohash);
    }

    static ChargingSiteUsage site(String name, String geohash) {
        return new ChargingSiteUsage(new ChargingSite(UUID.randomUUID(), name, name, geohash, null, null, 2,
                ChargingSiteSource.REGISTER, ChargingSite.RegisterDetails.NONE, LocalDateTime.now()), LocalDateTime.now(), 3);
    }

    @Test
    void homeComesFirstThenRecentSitesThenNearbyStationsNumberedInOrder() {
        when(sites.recentlyUsed(USER)).thenReturn(List.of(site("Lidl", "u0wt8b2")));
        when(nearby.findNearbyStations(anyString())).thenReturn(Optional.of(List.of(station("EnBW", "u0wt8b1"))));

        VoiceContext ctx = builder.build(USER, car, 48.1, 11.5, BERLIN);

        assertThat(ctx.candidates()).hasSize(3);
        assertThat(ctx.candidates().get(0)).isInstanceOf(PlaceCandidate.Home.class);
        assertThat(ctx.candidates().get(1)).isInstanceOf(PlaceCandidate.Site.class);
        assertThat(ctx.candidates().get(2)).isInstanceOf(PlaceCandidate.Station.class);
        assertThat(ctx.toExtractionContext().candidates()).extracting(ExtractionContext.Candidate::index, ExtractionContext.Candidate::kind,
                        ExtractionContext.Candidate::name)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(0, "home", "Zuhause"),
                        org.assertj.core.groups.Tuple.tuple(1, "station", "Lidl"),
                        org.assertj.core.groups.Tuple.tuple(2, "station", "EnBW"));
    }

    @Test
    void nearbyStationAlreadyKnownAsRecentSiteIsListedOnce() {
        when(sites.recentlyUsed(USER)).thenReturn(List.of(site("EnBW", "u0wt8b1")));
        when(nearby.findNearbyStations(anyString())).thenReturn(Optional.of(List.of(station("ENBW ", "u0wt8b1"))));

        assertThat(builder.build(USER, car, 48.1, 11.5, BERLIN).candidates()).hasSize(2);
    }

    @Test
    void emptyStandardRadiusFallsBackToTheWideSearch() {
        when(nearby.findNearbyStations(anyString())).thenReturn(Optional.of(List.of()));
        when(nearby.findNearbyStations(anyString(), org.mockito.ArgumentMatchers.eq(NearbyCpoService.MAX_RADIUS_METERS)))
                .thenReturn(Optional.of(List.of(station("Fastned", "u0wt9zz"))));

        assertThat(builder.build(USER, car, 48.1, 11.5, BERLIN).candidates()).hasSize(2);
    }

    @Test
    void withoutPositionNoRegisterLookup() {
        builder.build(USER, car, null, null, BERLIN);

        verifyNoInteractions(nearby);
    }

    @Test
    void positionIsCoarsenedToAGeohashBeforeTheLookup() {
        when(nearby.findNearbyStations(anyString())).thenReturn(Optional.of(List.of(station("EnBW", "u0wt8b1"))));

        builder.build(USER, car, 48.137154, 11.576124, BERLIN);

        verify(nearby).findNearbyStations("u281z7j");
        verify(nearby, never()).findNearbyStations(anyString(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void todayIsTheUsersLocalDateWithWeekdayAndZone() {
        assertThat(builder.build(USER, car, null, null, BERLIN).today())
                .isEqualTo("2026-10-04 (Sonntag), Zeitzone Europe/Berlin");
    }

    @Test
    void vehicleUsesTheSohAdjustedCapacity() {
        assertThat(builder.build(USER, car, null, null, BERLIN).vehicle()).isEqualTo("Nutzbare Akkukapazität 73.15 kWh");
    }

    @Test
    void onlyActiveTariffsAreIndexedWithLabel() {
        when(providers.getAll(USER)).thenReturn(List.of(
                tariff("EnBW mobility+", "ADAC", null),
                tariff("Maingau Energie", null, LocalDate.of(2026, 1, 31)),
                tariff("Octopus", "octopus", null)));

        VoiceContext ctx = builder.build(USER, car, null, null, BERLIN);

        assertThat(ctx.toExtractionContext().tariffs()).extracting(ExtractionContext.Tariff::name)
                .containsExactly("EnBW mobility+ (ADAC)", "Octopus");
        assertThat(ctx.tariffs().get(1).providerName()).isEqualTo("Octopus");
    }

    @Test
    void lastOdometerIsTheHighestOfTheRecentLogsAndOperatorsFeedTheBias() {
        when(logs.findLatestByCarId(car.getId(), VoiceContextBuilder.RECENT_LOGS)).thenReturn(List.of(
                log(31_207, "IONITY"), log(30_950, null), log(null, "Allego")));

        VoiceContext ctx = builder.build(USER, car, null, null, BERLIN);

        assertThat(ctx.lastOdometerKm()).isEqualTo(31_207);
        assertThat(ctx.biasTerms()).contains("IONITY", "Allego", "kWh");
    }

    static UserChargingProviderResponse tariff(String name, String label, LocalDate until) {
        return new UserChargingProviderResponse(UUID.randomUUID(), name, null, label, null, null, null, null,
                LocalDate.of(2025, 1, 1), until, false);
    }

    static EvLog log(Integer odometer, String cpo) {
        return EvLog.builder().id(UUID.randomUUID()).carId(UUID.randomUUID()).kwhCharged(BigDecimal.TEN)
                .costEur(BigDecimal.ONE).odometerKm(odometer).cpoName(cpo).loggedAt(LocalDateTime.now()).build();
    }
}
