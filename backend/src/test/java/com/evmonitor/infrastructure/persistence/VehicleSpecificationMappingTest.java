package com.evmonitor.infrastructure.persistence;

import com.evmonitor.domain.VehicleSpecification;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class VehicleSpecificationMappingTest {

    private final PostgresVehicleSpecificationRepositoryImpl repository =
            new PostgresVehicleSpecificationRepositoryImpl(null);

    @Test
    void toDomain_withChargingEfficiencies_mapsBothFields() {
        VehicleSpecificationEntity entity = buildEntity();
        entity.setChargingEfficiencyDc(new BigDecimal("0.9500"));
        entity.setChargingEfficiencyAc(new BigDecimal("0.8800"));

        VehicleSpecification domain = invokeToDomain(entity);

        assertThat(domain.getChargingEfficiencyDc()).isEqualByComparingTo(new BigDecimal("0.9500"));
        assertThat(domain.getChargingEfficiencyAc()).isEqualByComparingTo(new BigDecimal("0.8800"));
    }

    @Test
    void toDomain_withNullChargingEfficiencies_mapsNulls() {
        VehicleSpecificationEntity entity = buildEntity();
        entity.setChargingEfficiencyDc(null);
        entity.setChargingEfficiencyAc(null);

        VehicleSpecification domain = invokeToDomain(entity);

        assertThat(domain.getChargingEfficiencyDc()).isNull();
        assertThat(domain.getChargingEfficiencyAc()).isNull();
    }

    // --- helpers ---

    private VehicleSpecificationEntity buildEntity() {
        VehicleSpecificationEntity e = new VehicleSpecificationEntity();
        e.setId(UUID.randomUUID());
        e.setCarBrand("Tesla");
        e.setCarModel("Model 3");
        e.setBatteryCapacityKwh(new BigDecimal("75.00"));
        e.setOfficialRangeKm(new BigDecimal("500.00"));
        e.setOfficialConsumptionKwhPer100km(new BigDecimal("15.00"));
        e.setWltpType("COMBINED");
        e.setRatingSource("WLTP");
        e.setCreatedAt(LocalDateTime.now());
        e.setUpdatedAt(LocalDateTime.now());
        e.setVariantName("");
        return e;
    }

    /**
     * Reflective call to the private toDomain method so we can test mapping
     * without a real JPA repository/database.
     */
    private VehicleSpecification invokeToDomain(VehicleSpecificationEntity entity) {
        try {
            var method = PostgresVehicleSpecificationRepositoryImpl.class
                    .getDeclaredMethod("toDomain", VehicleSpecificationEntity.class);
            method.setAccessible(true);
            return (VehicleSpecification) method.invoke(repository, entity);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
