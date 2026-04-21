package com.evmonitor.infrastructure.persistence;

import com.evmonitor.domain.VehicleSpecification;
import com.evmonitor.domain.VehicleSpecificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PostgresVehicleSpecificationRepositoryImpl implements VehicleSpecificationRepository {

    private final JpaVehicleSpecificationRepository jpaRepository;

    @Override
    public VehicleSpecification save(VehicleSpecification vehicleSpecification) {
        VehicleSpecificationEntity entity = toEntity(vehicleSpecification);
        VehicleSpecificationEntity savedEntity = jpaRepository.save(entity);
        return toDomain(savedEntity);
    }

    @Override
    public Optional<VehicleSpecification> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<VehicleSpecification> findByCarBrandAndModelAndCapacityAndType(
            String carBrand,
            String carModel,
            BigDecimal batteryCapacityKwh,
            VehicleSpecification.WltpType wltpType) {
        // Lookup is always WLTP-specific (used in car-setup flow)
        return jpaRepository.findByCarBrandAndCarModelAndBatteryCapacityKwhAndWltpTypeAndRatingSource(
                carBrand,
                carModel,
                batteryCapacityKwh,
                wltpType.name(),
                VehicleSpecification.RatingSource.WLTP.name()
        ).map(this::toDomain);
    }

    @Override
    public Optional<VehicleSpecification> findByCarBrandAndModelAndCapacityAndTypeAndSource(
            String carBrand,
            String carModel,
            BigDecimal batteryCapacityKwh,
            VehicleSpecification.WltpType wltpType,
            VehicleSpecification.RatingSource ratingSource) {
        return jpaRepository.findByCarBrandAndCarModelAndBatteryCapacityKwhAndWltpTypeAndRatingSource(
                carBrand,
                carModel,
                batteryCapacityKwh,
                wltpType.name(),
                ratingSource.name()
        ).map(this::toDomain);
    }

    @Override
    public boolean existsByCarBrandAndModelAndCapacityAndTypeAndSource(
            String carBrand,
            String carModel,
            BigDecimal batteryCapacityKwh,
            VehicleSpecification.WltpType wltpType,
            VehicleSpecification.RatingSource ratingSource) {
        return jpaRepository.existsByCarBrandAndCarModelAndBatteryCapacityKwhAndWltpTypeAndRatingSource(
                carBrand,
                carModel,
                batteryCapacityKwh,
                wltpType.name(),
                ratingSource.name()
        );
    }

    private VehicleSpecificationEntity toEntity(VehicleSpecification domain) {
        VehicleSpecificationEntity entity = new VehicleSpecificationEntity();
        entity.setId(domain.getId());
        entity.setCarBrand(domain.getCarBrand());
        entity.setCarModel(domain.getCarModel());
        entity.setBatteryCapacityKwh(domain.getBatteryCapacityKwh());
        entity.setOfficialRangeKm(domain.getOfficialRangeKm());
        entity.setOfficialConsumptionKwhPer100km(domain.getOfficialConsumptionKwhPer100km());
        entity.setWltpType(domain.getWltpType().name());
        entity.setRatingSource(domain.getRatingSource().name());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());
        entity.setVariantName(domain.getVariantName());
        entity.setNetBatteryCapacityKwh(domain.getNetBatteryCapacityKwh());
        return entity;
    }

    private VehicleSpecification toDomain(VehicleSpecificationEntity entity) {
        VehicleSpecification.RatingSource ratingSource = entity.getRatingSource() != null
                ? VehicleSpecification.RatingSource.valueOf(entity.getRatingSource())
                : VehicleSpecification.RatingSource.WLTP;
        return new VehicleSpecification(
                entity.getId(),
                entity.getCarBrand(),
                entity.getCarModel(),
                entity.getBatteryCapacityKwh(),
                entity.getOfficialRangeKm(),
                entity.getOfficialConsumptionKwhPer100km(),
                VehicleSpecification.WltpType.valueOf(entity.getWltpType()),
                ratingSource,
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVariantName(),
                entity.getNetBatteryCapacityKwh()
        );
    }
}
