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
    private final PublicEmbedService service = new PublicEmbedService(publicModelService, "https://ev-monitor.net");

    @Test
    void tileUsesVariantWithMostTripsAndRoundsRangesToTens() {
        // 77 kWh netto: 77 × 0,9 / 17,2 = 402,9 → 400; Sommer 15,5 → 447,1 → 450; Winter 21,4 → 323,8 → 320
        var seasonal = seasonal(new BigDecimal("15.5"), new BigDecimal("21.4"), 40, 25);
        var small = variant(new BigDecimal("58"), new BigDecimal("16.0"), 20, null);
        var large = variant(new BigDecimal("77"), new BigDecimal("17.2"), 150, seasonal);
        when(publicModelService.getModelStats("VW", "ID.3", false))
                .thenReturn(Optional.of(stats(List.of(small, large), new BigDecimal("16.80"), 812, 34)));

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
                .thenReturn(Optional.of(stats(List.of(v), new BigDecimal("17.2"), 300, 10)));

        ModelTileResponse tile = service.getModelTile("VW", "ID.3");

        assertThat(tile.summerRangeKm()).isNull();
        assertThat(tile.winterRangeKm()).isNull();
        assertThat(tile.typicalRangeKm()).isEqualTo(400);
    }

    @Test
    void fallsBackToModelAverageAndLargestBatteryWhenNoVariantHasRealData() {
        var a = variant(new BigDecimal("58"), null, null, null);
        var b = variant(new BigDecimal("77"), null, null, null);
        when(publicModelService.getModelStats("VW", "ID.3", false))
                .thenReturn(Optional.of(stats(List.of(a, b), new BigDecimal("17.25"), 12, 2)));

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
                .thenReturn(Optional.of(stats(List.of(v), null, 0, 0)));

        ModelTileResponse tile = service.getModelTile("VW", "ID.3");

        assertThat(tile.hasData()).isFalse();
        assertThat(tile.modelDisplayName()).isEqualTo("VW ID.3");
        assertThat(tile.modelUrl()).isEqualTo("https://ev-monitor.net/modelle/Volkswagen/ID.3");
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
                                                  BigDecimal avg, int logCount, int cars) {
        return new PublicModelStatsResponse("VW", "ID_3", "VW", "VW ID.3", "COMPACT", "Kompakt",
                logCount, cars, cars, null, null, null, null, avg, 0, null,
                variants, null, null, List.of(), List.of());
    }
}
