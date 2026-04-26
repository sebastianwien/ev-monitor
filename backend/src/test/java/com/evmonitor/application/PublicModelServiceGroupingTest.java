package com.evmonitor.application;

import com.evmonitor.infrastructure.persistence.VehicleSpecificationEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class PublicModelServiceGroupingTest {

    @Mock private com.evmonitor.infrastructure.persistence.JpaEvLogRepository evLogRepository;
    @Mock private com.evmonitor.infrastructure.persistence.JpaVehicleSpecificationRepository vehicleSpecificationRepository;
    @Mock private com.evmonitor.infrastructure.persistence.JpaUserRepository userRepository;
    @Mock private com.evmonitor.domain.CarRepository carRepository;
    @Mock private EvLogStatisticsService evLogStatisticsService;

    private PublicModelService service;

    @BeforeEach
    void setUp() {
        service = new PublicModelService(evLogRepository, vehicleSpecificationRepository, userRepository, carRepository, evLogStatisticsService);
    }

    @Test
    void groupByTrimLevel_noTrimLevels_eachSpecItsOwnGroup() {
        var spec1 = spec(null);
        var spec2 = spec(null);

        var groups = service.groupByTrimLevel(List.of(spec1, spec2));

        assertEquals(2, groups.size());
        assertNull(groups.get(0).trimLevel());
        assertNull(groups.get(1).trimLevel());
        assertEquals(1, groups.get(0).specs().size());
        assertEquals(1, groups.get(1).specs().size());
    }

    @Test
    void groupByTrimLevel_sameTrimLevel_mergedIntoOneGroup() {
        var spec1 = spec("Long Range AWD");
        var spec2 = spec("Long Range AWD");
        var spec3 = spec("Long Range AWD");

        var groups = service.groupByTrimLevel(List.of(spec1, spec2, spec3));

        assertEquals(1, groups.size());
        assertEquals("Long Range AWD", groups.get(0).trimLevel());
        assertEquals(3, groups.get(0).specs().size());
    }

    @Test
    void groupByTrimLevel_differentTrimLevels_separateGroups() {
        var standard    = spec("Standard Range");
        var lrAwd1      = spec("Long Range AWD");
        var lrAwd2      = spec("Long Range AWD");
        var performance = spec("Performance");

        var groups = service.groupByTrimLevel(List.of(standard, lrAwd1, lrAwd2, performance));

        assertEquals(3, groups.size());
        assertEquals("Standard Range",  groups.get(0).trimLevel());
        assertEquals("Long Range AWD",  groups.get(1).trimLevel());
        assertEquals("Performance",     groups.get(2).trimLevel());
        assertEquals(2, groups.get(1).specs().size());
    }

    @Test
    void groupByTrimLevel_mixed_trimGroupedAndSoloGroups() {
        var withTrim    = spec("Performance");
        var withoutTrim = spec(null);

        var groups = service.groupByTrimLevel(List.of(withTrim, withoutTrim));

        assertEquals(2, groups.size());
        assertEquals("Performance", groups.get(0).trimLevel());
        assertNull(groups.get(1).trimLevel());
        assertEquals(1, groups.get(1).specs().size());
    }

    @Test
    void groupByTrimLevel_emptyList_returnsEmptyList() {
        var groups = service.groupByTrimLevel(List.of());
        assertTrue(groups.isEmpty());
    }

    @Test
    void groupByTrimLevel_preservesInsertionOrder() {
        var lrRwd = spec("Long Range RWD");
        var lrAwd = spec("Long Range AWD");
        var perf  = spec("Performance");
        var std   = spec("Standard Range");

        var groups = service.groupByTrimLevel(List.of(lrRwd, lrAwd, perf, std));

        assertEquals(List.of("Long Range RWD", "Long Range AWD", "Performance", "Standard Range"),
                groups.stream().map(PublicModelService.TrimGroup::trimLevel).toList());
    }

    @Test
    void wltpVariant_hasRangeMinKm() {
        var variant = new PublicModelStatsResponse.WltpVariant(
                BigDecimal.valueOf(75), "variant", "Long Range AWD",
                BigDecimal.valueOf(602), BigDecimal.valueOf(530),
                BigDecimal.valueOf(15.5), BigDecimal.valueOf(14.0), BigDecimal.valueOf(17.0),
                BigDecimal.valueOf(18.0), null, null, 100, null, null
        );
        assertEquals(BigDecimal.valueOf(530), variant.wltpRangeMinKm());
        assertEquals(BigDecimal.valueOf(602), variant.wltpRangeKm());
    }

    @Test
    void wltpVariant_singleSpec_rangeMinIsNull() {
        var variant = new PublicModelStatsResponse.WltpVariant(
                BigDecimal.valueOf(75), "variant", "Standard Range",
                BigDecimal.valueOf(491), null,
                BigDecimal.valueOf(15.1), null, null,
                null, null, null, 0, null, null
        );
        assertNull(variant.wltpRangeMinKm());
    }

    private VehicleSpecificationEntity spec(String trimLevel) {
        return spec(trimLevel, BigDecimal.valueOf(500));
    }

    private VehicleSpecificationEntity spec(String trimLevel, BigDecimal rangeKm) {
        var e = new VehicleSpecificationEntity();
        e.setId(UUID.randomUUID());
        e.setCarBrand("TESLA");
        e.setCarModel("MODEL_3");
        e.setBatteryCapacityKwh(BigDecimal.valueOf(75));
        e.setOfficialRangeKm(rangeKm);
        e.setOfficialConsumptionKwhPer100km(BigDecimal.valueOf(15));
        e.setWltpType("COMBINED");
        e.setRatingSource("WLTP");
        e.setVariantName(trimLevel != null ? trimLevel : "some variant");
        e.setCreatedAt(LocalDateTime.now());
        e.setUpdatedAt(LocalDateTime.now());
        e.setTrimLevel(trimLevel);
        return e;
    }
}
