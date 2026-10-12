package com.evmonitor.application;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicEmbedServiceTest {

    private final PublicModelService publicModelService = mock(PublicModelService.class);
    private final PublicEmbedService service = new PublicEmbedService(publicModelService, new PlausibilityProperties(), "https://ev-monitor.net");

    @Test
    void tileUsesModelAverageOnTheBatteryOfTheMostMeasuredVariantAndRoundsRangesToTens() {
        // Verbrauch: Modellmittel 17,2 wie Titel und Meta-Description, nicht 18,9 der Variante.
        // Akku und Saison: Variante mit den meisten Fahrten (77 kWh), mit der die Modellseite öffnet.
        // 77 × 0,9 / 17,2 = 402,9 → 400; Sommer 15,5 → 447,1 → 450; Winter 21,4 → 323,8 → 320
        var seasonal = seasonal(new BigDecimal("15.5"), new BigDecimal("21.4"), 40, 25);
        var small = variant(new BigDecimal("58"), new BigDecimal("16.0"), 20, null);
        var large = variant(new BigDecimal("77"), new BigDecimal("18.9"), 150, seasonal);
        var largest = variant(new BigDecimal("90"), null, null, null);
        when(publicModelService.getModelStats("VW", "ID.3", false))
                .thenReturn(Optional.of(stats(List.of(small, large, largest), new BigDecimal("17.2"), 812, 34, Extras.NONE)));

        ModelTileResponse tile = service.getModelTile("VW", "ID.3");

        assertThat(tile.hasData()).isTrue();
        assertThat(tile.modelDisplayName()).isEqualTo("VW ID.3");
        assertThat(tile.realConsumptionKwhPer100km()).isEqualByComparingTo("17.2");
        assertThat(tile.typicalRangeKm()).isEqualTo(400);
        assertThat(tile.summerRangeKm()).isEqualTo(450);
        assertThat(tile.winterRangeKm()).isEqualTo(320);
        assertThat(tile.logCount()).isEqualTo(812);
        assertThat(tile.uniqueCars()).isEqualTo(34);
        assertThat(tile.modelUrl()).isEqualTo("https://ev-monitor.net/modelle/Volkswagen/ID.3");
    }

    @Test
    void seasonalRangesAreNullBelowTenLogsPerSeason() {
        var thin = seasonal(new BigDecimal("15.5"), new BigDecimal("21.4"), 40, 9);
        var v = variant(new BigDecimal("77"), new BigDecimal("17.2"), 150, thin);
        when(publicModelService.getModelStats("VW", "ID.3", false))
                .thenReturn(Optional.of(stats(List.of(v), new BigDecimal("17.2"), 300, 10, Extras.NONE)));

        ModelTileResponse tile = service.getModelTile("VW", "ID.3");

        assertThat(tile.summerRangeKm()).isNull();
        assertThat(tile.winterRangeKm()).isNull();
        assertThat(tile.typicalRangeKm()).isEqualTo(400);
    }

    @Test
    void withoutMeasuredVariantTheLargestBatteryCarriesTheRange() {
        var a = variant(new BigDecimal("58"), null, null, null);
        var b = variant(new BigDecimal("77"), null, null, null);
        when(publicModelService.getModelStats("VW", "ID.3", false))
                .thenReturn(Optional.of(stats(List.of(a, b), new BigDecimal("17.25"), 12, 2, Extras.NONE)));

        ModelTileResponse tile = service.getModelTile("VW", "ID.3");

        assertThat(tile.hasData()).isTrue();
        assertThat(tile.realConsumptionKwhPer100km()).isEqualByComparingTo("17.3");
        assertThat(tile.typicalRangeKm()).isEqualTo(400);
    }

    @Test
    void unknownModelReturnsFallbackWithoutData() {
        when(publicModelService.getModelStats("Foo", "Bar", false)).thenReturn(Optional.empty());

        ModelTileResponse tile = service.getModelTile("Foo", "Bar");

        assertThat(tile.hasData()).isFalse();
        assertThat(tile.modelUrl()).isNull();
        assertThat(tile.realConsumptionKwhPer100km()).isNull();
    }

    @Test
    void knownModelWithoutConsumptionHasNoDataButKeepsModelUrl() {
        var v = variant(new BigDecimal("77"), null, null, null);
        when(publicModelService.getModelStats("VW", "ID.3", false))
                .thenReturn(Optional.of(stats(List.of(v), null, 0, 0, Extras.NONE)));

        ModelTileResponse tile = service.getModelTile("VW", "ID.3");

        assertThat(tile.hasData()).isFalse();
        assertThat(tile.modelDisplayName()).isEqualTo("VW ID.3");
        assertThat(tile.modelUrl()).isEqualTo("https://ev-monitor.net/modelle/Volkswagen/ID.3");
    }

    @Test
    void chargeCostsComeFromThePriceAtThePlugIncludingChargingLosses() {
        // Ø-Ladung: 29,94 kWh an der Säule × 0,2958 €/kWh = 8,856 → 8,86 €
        // Schnellladestopp 10-80 % auf dem Akku der meistgemessenen Variante (77 kWh):
        // 77 × 0,7 / 0,95 (DC-Verluste) = 56,737 kWh × 0,34 € = 19,29 €
        var v = variant(new BigDecimal("77"), new BigDecimal("17.2"), 150, null);
        var extras = new Extras(null, new BigDecimal("0.2958"), new BigDecimal("0.34"), new BigDecimal("29.94"));
        when(publicModelService.getModelStats("VW", "ID.3", false))
                .thenReturn(Optional.of(stats(List.of(v), new BigDecimal("17.2"), 300, 10, extras)));

        ModelTileResponse tile = service.getModelTile("VW", "ID.3");

        assertThat(tile.typicalChargeCostEur()).isEqualByComparingTo("8.86");
        assertThat(tile.fastStopCostEur()).isEqualByComparingTo("19.29");
    }

    @Test
    void fastStopFallsBackToTheAveragePriceWithoutDcPriceAndCostsAreNullWithoutPrices() {
        var v = variant(new BigDecimal("77"), new BigDecimal("17.2"), 150, null);
        when(publicModelService.getModelStats("VW", "ID.3", false))
                .thenReturn(Optional.of(stats(List.of(v), new BigDecimal("17.2"), 300, 10,
                        new Extras(null, new BigDecimal("0.30"), null, new BigDecimal("30")))));
        // 56,737 kWh × 0,30 € = 17,02 €
        assertThat(service.getModelTile("VW", "ID.3").fastStopCostEur()).isEqualByComparingTo("17.02");

        when(publicModelService.getModelStats("VW", "ID.3", false))
                .thenReturn(Optional.of(stats(List.of(v), new BigDecimal("17.2"), 300, 10, Extras.NONE)));
        ModelTileResponse tile = service.getModelTile("VW", "ID.3");
        assertThat(tile.typicalChargeCostEur()).isNull();
        assertThat(tile.fastStopCostEur()).isNull();
    }

    @Test
    void seasonalConsumptionIsTheModelWideMeasurementFromTenLogsPerSeason() {
        // Modellweit wie Ø-Verbrauch und Ladungszahl der Kachel, nicht die Saison der Variante
        var variantSeason = seasonal(new BigDecimal("15.5"), new BigDecimal("21.4"), 40, 25);
        var v = variant(new BigDecimal("77"), new BigDecimal("17.2"), 150, variantSeason);
        var modelSeason = seasonal(new BigDecimal("19.0"), new BigDecimal("22.0"), 1861, 1565);
        when(publicModelService.getModelStats("VW", "ID.3", false))
                .thenReturn(Optional.of(stats(List.of(v), new BigDecimal("20.0"), 3426, 42,
                        new Extras(modelSeason, null, null, null))));

        ModelTileResponse tile = service.getModelTile("VW", "ID.3");

        assertThat(tile.summerConsumptionKwhPer100km()).isEqualByComparingTo("19.0");
        assertThat(tile.winterConsumptionKwhPer100km()).isEqualByComparingTo("22.0");
        assertThat(tile.summerLogCount()).isEqualTo(1861);
        assertThat(tile.winterLogCount()).isEqualTo(1565);

        var thin = seasonal(new BigDecimal("19.0"), new BigDecimal("22.0"), 1861, 9);
        when(publicModelService.getModelStats("VW", "ID.3", false))
                .thenReturn(Optional.of(stats(List.of(v), new BigDecimal("20.0"), 1870, 42, new Extras(thin, null, null, null))));
        ModelTileResponse thinTile = service.getModelTile("VW", "ID.3");
        assertThat(thinTile.summerConsumptionKwhPer100km()).isNull();
        assertThat(thinTile.winterConsumptionKwhPer100km()).isNull();
        assertThat(thinTile.winterLogCount()).isNull();
    }

    /** Model-wide seasons and charge prices; NONE for tests that only look at consumption and range. */
    private record Extras(PublicModelStatsResponse.SeasonalDistribution seasonal,
                          BigDecimal avgPrice, BigDecimal dcPrice, BigDecimal kwhPerSession) {
        static final Extras NONE = new Extras(null, null, null, null);
    }

    private static PublicModelStatsResponse.SeasonalDistribution seasonal(
            BigDecimal summer, BigDecimal winter, int summerLogs, int winterLogs) {
        return new PublicModelStatsResponse.SeasonalDistribution(60, 40, summer, winter, null, summerLogs, winterLogs);
    }

    private static PublicModelStatsResponse.WltpVariant variant(BigDecimal netKwh, BigDecimal real, Integer trips,
                                                                PublicModelStatsResponse.SeasonalDistribution s) {
        return new PublicModelStatsResponse.WltpVariant(netKwh, null, null, null, null, null, null, null,
                real, null, null, trips, null, null, s);
    }

    private static PublicModelStatsResponse stats(List<PublicModelStatsResponse.WltpVariant> variants,
                                                  BigDecimal avg, int logCount, int cars, Extras extras) {
        return new PublicModelStatsResponse("VW", "ID_3", "VW", "VW ID.3", "COMPACT", "Kompakt",
                logCount, cars, cars, extras.avgPrice(), null, extras.dcPrice(), extras.kwhPerSession(), avg, 0, null,
                variants, null, extras.seasonal(), List.of(), List.of(), null);
    }
}
