package com.evmonitor.infrastructure.persistence;

import com.evmonitor.domain.VehicleSpecification;
import com.evmonitor.domain.VehicleSpecificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
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
        List<VehicleSpecificationEntity> results =
                jpaRepository.findByCarBrandAndCarModelAndBatteryCapacityKwhAndWltpTypeAndRatingSource(
                        carBrand, carModel, batteryCapacityKwh,
                        wltpType.name(), VehicleSpecification.RatingSource.WLTP.name());
        return pickBestMatch(results).map(this::toDomain);
    }

    @Override
    public Optional<VehicleSpecification> findByCarBrandAndModelAndCapacityAndTypeAndSource(
            String carBrand,
            String carModel,
            BigDecimal batteryCapacityKwh,
            VehicleSpecification.WltpType wltpType,
            VehicleSpecification.RatingSource ratingSource) {
        List<VehicleSpecificationEntity> results =
                jpaRepository.findByCarBrandAndCarModelAndBatteryCapacityKwhAndWltpTypeAndRatingSource(
                        carBrand, carModel, batteryCapacityKwh,
                        wltpType.name(), ratingSource.name());
        return pickBestMatch(results).map(this::toDomain);
    }

    /**
     * When multiple variants share the same kWh, prefer the currently available one
     * (available_to IS NULL), then the one with the latest available_from.
     */
    private Optional<VehicleSpecificationEntity> pickBestMatch(List<VehicleSpecificationEntity> candidates) {
        if (candidates.isEmpty()) return Optional.empty();
        if (candidates.size() == 1) return Optional.of(candidates.get(0));
        return candidates.stream()
                .max(Comparator
                        .comparingInt((VehicleSpecificationEntity e) -> e.getAvailableTo() == null ? 1 : 0)
                        .thenComparing(e -> e.getAvailableFrom() != null ? e.getAvailableFrom() : LocalDate.MIN));
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
        entity.setAvailableFrom(domain.getAvailableFrom());
        entity.setAvailableTo(domain.getAvailableTo());
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
                entity.getNetBatteryCapacityKwh(),
                entity.getAvailableFrom(),
                entity.getAvailableTo()
        );
    }
}
