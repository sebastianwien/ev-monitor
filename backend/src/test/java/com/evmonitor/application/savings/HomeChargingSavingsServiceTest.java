package com.evmonitor.application.savings;

import com.evmonitor.application.savings.HomeChargingSavingsService.HomeChargingProfile;
import com.evmonitor.application.savings.HomeChargingSavingsService.HomeChargingProfileProvider;
import com.evmonitor.infrastructure.persistence.ChargingSavingsQueryRepository;
import com.evmonitor.infrastructure.persistence.ChargingSavingsQueryRepository.RegionMedian;
import com.evmonitor.infrastructure.persistence.ChargingSavingsQueryRepository.YearPrice;
import com.evmonitor.infrastructure.persistence.ChargingSavingsQueryRepository.YearTotals;
import com.evmonitor.infrastructure.persistence.ChargingSavingsQueryRepository.WeightedPrice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Zusammenspiel der Stufen. Die Rechenlogik selbst liegt in ChargingPriceResolver und
 * ChargingSavingsCalculator - hier geht es darum, welche Stufe unter welchen Bedingungen
 * zum Zug kommt und dass die Kachel das Ergebnis einordnen kann.
 */
@ExtendWith(MockitoExtension.class)
class HomeChargingSavingsServiceTest {

    private static final UUID USER = UUID.randomUUID();

    @Mock ChargingSavingsQueryRepository repo;
    @Mock ChargingPriceCache priceCache;
    @Mock HomeChargingProfileProvider profiles;

    private HomeChargingSavingsService service;

    private static BigDecimal eur(String s) { return new BigDecimal(s); }

    @BeforeEach
    void setUp() {
        service = new HomeChargingSavingsService(repo, priceCache, profiles, List.of(5, 3));
        lenient().when(profiles.forUser(USER)).thenReturn(new HomeChargingProfile("DE", null));
        lenient().when(repo.monthsOfHomeCharging(USER)).thenReturn(eur("12"));
        lenient().when(repo.homeYearTotals(USER)).thenReturn(List.of(
                new YearTotals(2026, eur("640"), eur("172.80"))));
        lenient().when(priceCache.publicPriceByYear(eq(USER), eq("DE"), anyInt(), anyInt())).thenReturn(List.of(
                new YearPrice(2026, eur("0.40"), "COUNTRY")));
        lenient().when(repo.homeWeightedPrice(USER)).thenReturn(new WeightedPrice(eur("0.27"), 23));
    }

    @Test
    void ownPublicLogs_beatRegionAndCountry() {
        when(repo.ownPublicPrices(USER)).thenReturn(
                List.of(eur("0.45"), eur("0.45"), eur("0.45"), eur("0.45"), eur("0.45")));

        ChargingSavings result = service.calculate(USER);

        assertEquals(PriceSource.OWN_PUBLIC, result.publicPrice().source());
        verify(priceCache, never()).countryMedian(any(), anyInt());
    }

    /** Die feine Zelle traegt, wenn sie das Mindestmass erreicht. */
    @Test
    void withoutOwnPublicLogs_usesFinestRegionThatQualifies() {
        when(repo.ownPublicPrices(USER)).thenReturn(List.of());
        when(repo.homeGeohash(USER)).thenReturn("u1hcy78");
        when(priceCache.regionMedian(eq("u1hcy"), eq("DE"), anyInt(), anyInt()))
                .thenReturn(new RegionMedian(eur("0.44"), 18));

        ChargingSavings result = service.calculate(USER);

        assertEquals(PriceSource.REGION, result.publicPrice().source());
        assertEquals(18, result.publicPrice().sampleSize());
        verify(priceCache, never()).regionMedian(eq("u1h"), any(), anyInt(), anyInt());
    }

    /**
     * Auf Prod erfuellt bei Geohash-5 genau eine Zelle das Mindestmass. Die feine Stufe
     * muss deshalb geraeuschlos durchfallen statt eine Zahl aus drei Ladungen zu liefern.
     */
    @Test
    void thinFineCell_fallsThroughToCoarserPrefix() {
        when(repo.ownPublicPrices(USER)).thenReturn(List.of());
        when(repo.homeGeohash(USER)).thenReturn("u1hcy78");
        when(priceCache.regionMedian(eq("u1hcy"), eq("DE"), anyInt(), anyInt())).thenReturn(null);
        when(priceCache.regionMedian(eq("u1h"), eq("DE"), anyInt(), anyInt()))
                .thenReturn(new RegionMedian(eur("0.41"), 120));

        ChargingSavings result = service.calculate(USER);

        assertEquals(PriceSource.REGION, result.publicPrice().source());
        assertEquals(120, result.publicPrice().sampleSize());
    }

    @Test
    void allRegionsThin_fallsBackToCountry() {
        when(repo.ownPublicPrices(USER)).thenReturn(List.of());
        when(repo.homeGeohash(USER)).thenReturn("u1hcy78");
        when(priceCache.regionMedian(any(), any(), anyInt(), anyInt())).thenReturn(null);
        when(priceCache.countryMedian(eq("DE"), anyInt())).thenReturn(new RegionMedian(eur("0.40"), 2659));

        ChargingSavings result = service.calculate(USER);

        assertEquals(PriceSource.COUNTRY, result.publicPrice().source());
        assertEquals(0, eur("83.20").compareTo(result.savingsEur()));  // 640 * (0,40 - 0,27)
    }

    /** Ohne verorteten Heimladeort gibt es keinen Anker - die Regionsstufe entfaellt. */
    @Test
    void withoutHomeGeohash_skipsRegionEntirely() {
        when(repo.ownPublicPrices(USER)).thenReturn(List.of());
        when(repo.homeGeohash(USER)).thenReturn(null);
        when(priceCache.countryMedian(eq("DE"), anyInt())).thenReturn(new RegionMedian(eur("0.40"), 2659));

        ChargingSavings result = service.calculate(USER);

        assertEquals(PriceSource.COUNTRY, result.publicPrice().source());
        verify(priceCache, never()).regionMedian(any(), any(), anyInt(), anyInt());
    }

    /**
     * Die kumulierte Ersparnis kommt aus den Jahren selbst, nicht aus einer Hochrechnung.
     * Auf Prod lagen zwischen dem ersten Log und der ersten Heimladung bis zu 3,4 Jahre -
     * hochgerechnet haette die Kachel Ersparnis fuer Jahre ausgewiesen, in denen es die
     * Wallbox noch gar nicht gab.
     */
    @Test
    void recovered_isSummedFromActualYears_notExtrapolated() {
        when(repo.ownPublicPrices(USER)).thenReturn(List.of());
        when(repo.homeGeohash(USER)).thenReturn(null);
        when(priceCache.countryMedian(eq("DE"), anyInt())).thenReturn(new RegionMedian(eur("0.40"), 2659));
        when(repo.homeYearTotals(USER)).thenReturn(List.of(
                new YearTotals(2025, eur("300"), eur("84.00")),
                new YearTotals(2026, eur("640"), eur("172.80"))));
        when(priceCache.publicPriceByYear(eq(USER), eq("DE"), anyInt(), anyInt())).thenReturn(List.of(
                new YearPrice(2025, eur("0.55"), "COUNTRY"),
                new YearPrice(2026, eur("0.40"), "COUNTRY")));

        ChargingSavings result = service.calculate(USER);

        // 2025: 300*0,55 - 84 = 81,00   2026: 640*0,40 - 172,80 = 83,20
        assertEquals(0, eur("164.20").compareTo(result.recoveredEur()));
        assertEquals(2025, result.firstYear());
    }

    /** Jedes Jahr rechnet mit dem Preisniveau seines Jahres, nicht mit dem heutigen. */
    @Test
    void eachYear_usesItsOwnPublicPrice() {
        when(repo.ownPublicPrices(USER)).thenReturn(List.of());
        when(repo.homeGeohash(USER)).thenReturn(null);
        when(priceCache.countryMedian(eq("DE"), anyInt())).thenReturn(new RegionMedian(eur("0.40"), 2659));
        when(repo.homeYearTotals(USER)).thenReturn(List.of(
                new YearTotals(2025, eur("100"), eur("30.00")),
                new YearTotals(2026, eur("100"), eur("30.00"))));
        when(priceCache.publicPriceByYear(eq(USER), eq("DE"), anyInt(), anyInt())).thenReturn(List.of(
                new YearPrice(2025, eur("0.60"), "COUNTRY"),
                new YearPrice(2026, eur("0.40"), "COUNTRY")));

        ChargingSavings result = service.calculate(USER);

        assertEquals(2, result.yearlySavings().size());
        assertEquals(0, eur("30.00").compareTo(result.yearlySavings().get(0).savingsEur()));
        assertEquals(0, eur("10.00").compareTo(result.yearlySavings().get(1).savingsEur()));
    }

    /** Ein Jahr ohne belegbares oeffentliches Preisniveau faellt heraus. */
    @Test
    void yearWithoutPublicPrice_isDropped() {
        when(repo.ownPublicPrices(USER)).thenReturn(List.of());
        when(repo.homeGeohash(USER)).thenReturn(null);
        when(priceCache.countryMedian(eq("DE"), anyInt())).thenReturn(new RegionMedian(eur("0.40"), 2659));
        when(repo.homeYearTotals(USER)).thenReturn(List.of(
                new YearTotals(2024, eur("500"), eur("140.00")),
                new YearTotals(2026, eur("640"), eur("172.80"))));
        when(priceCache.publicPriceByYear(eq(USER), eq("DE"), anyInt(), anyInt())).thenReturn(List.of(
                new YearPrice(2024, null, "COUNTRY"),
                new YearPrice(2026, eur("0.40"), "COUNTRY")));

        ChargingSavings result = service.calculate(USER);

        assertEquals(1, result.yearlySavings().size());
        assertEquals(2026, result.firstYear());
    }

    /**
     * Unter der Relevanzschwelle sagt die Kachel nichts aus: aus einer Handvoll
     * Heimladungen laesst sich keine belastbare Ersparnis ableiten - dann lieber gar
     * keine Kachel als eine Zahl, die niemanden interessiert.
     */
    @Test
    void belowMinimumHomeKwh_noResult() {
        when(repo.ownPublicPrices(USER)).thenReturn(List.of());
        when(repo.homeGeohash(USER)).thenReturn(null);
        when(priceCache.countryMedian(eq("DE"), anyInt())).thenReturn(new RegionMedian(eur("0.40"), 2659));
        when(repo.homeYearTotals(USER)).thenReturn(List.of(
                new YearTotals(2026, eur("80"), eur("21.60"))));

        assertNull(service.calculate(USER));
    }

    /** Genau an der Schwelle traegt die Aussage - die Kachel erscheint. */
    @Test
    void atMinimumHomeKwh_result() {
        when(repo.ownPublicPrices(USER)).thenReturn(List.of());
        when(repo.homeGeohash(USER)).thenReturn(null);
        when(priceCache.countryMedian(eq("DE"), anyInt())).thenReturn(new RegionMedian(eur("0.40"), 2659));
        when(repo.homeYearTotals(USER)).thenReturn(List.of(
                new YearTotals(2026, eur("100"), eur("27.00"))));

        assertNotNull(service.calculate(USER));
    }

    /**
     * Kein Landes-Default fuer den Heimpreis: ohne eigene Logs und ohne Heimstrom-Karte
     * bleibt die Kachel leer, statt fremde Zahlen als die des Nutzers auszugeben.
     */
    @Test
    void withoutHomePrice_noResult() {
        when(repo.homeWeightedPrice(USER)).thenReturn(null);

        assertNull(service.calculate(USER));
        // Ohne Heimpreis wird die Vergleichskette gar nicht erst befragt.
        verify(repo, never()).ownPublicPrices(any());
    }

}
