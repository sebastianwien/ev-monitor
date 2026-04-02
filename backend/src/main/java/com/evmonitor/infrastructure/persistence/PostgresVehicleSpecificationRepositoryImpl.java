package com.evmonitor.infrastructure.persistence;

import com.evmonitor.domain.VehicleSpecification;
import com.evmonitor.domain.VehicleSpecificationRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

@Component
public class PostgresVehicleSpecificationRepositoryImpl implements VehicleSpecificationRepository {

    private final JpaVehicleSpecificationRepository jpaRepository;

    public PostgresVehicleSpecificationRepositoryImpl(JpaVehicleSpecificationRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public VehicleSpecification save(VehicleSpecification vehicleSpecification) {
        VehicleSpecificationEntity entity = toEntity(vehicleSpecification);
        VehicleSpecificationEntity savedEntity = jpaRepository.save(entity);
        return toDomain(savedEntity);
    }

    @Override
    public Optional<VehicleSpecification> findByCarBrandAndModelAndCapacityAndType(
            String carBrand,
            String carModel,
            BigDecimal batteryCapacityKwh,
            VehicleSpecification.WltpType wltpType) {
        return jpaRepository.findByCarBrandAndCarModelAndBatteryCapacityKwhAndWltpType(
                carBrand,
                carModel,
                batteryCapacityKwh,
                wltpType.name()
        ).map(this::toDomain);
    }

    @Override
    public boolean existsByCarBrandAndModelAndCapacityAndType(
            String carBrand,
            String carModel,
            BigDecimal batteryCapacityKwh,
            VehicleSpecification.WltpType wltpType) {
        return jpaRepository.existsByCarBrandAndCarModelAndBatteryCapacityKwhAndWltpType(
                carBrand,
                carModel,
                batteryCapacityKwh,
                wltpType.name()
        );
    }

    private VehicleSpecificationEntity toEntity(VehicleSpecification domain) {
        VehicleSpecificationEntity entity = new VehicleSpecificationEntity();
        entity.setId(domain.getId());
        entity.setCarBrand(domain.getCarBrand());
        entity.setCarModel(domain.getCarModel());
        entity.setBatteryCapacityKwh(domain.getBatteryCapacityKwh());
        entity.setWltpRangeKm(domain.getWltpRangeKm());
        entity.setWltpConsumptionKwhPer100km(domain.getWltpConsumptionKwhPer100km());
        entity.setWltpType(domain.getWltpType().name());
        entity.setVariantName(domain.getVariantName());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());
        return entity;
    }

    private VehicleSpecification toDomain(VehicleSpecificationEntity entity) {
        return new VehicleSpecification(
                entity.getId(),
                entity.getCarBrand(),
                entity.getCarModel(),
                entity.getBatteryCapacityKwh(),
                entity.getWltpRangeKm(),
                entity.getWltpConsumptionKwhPer100km(),
                VehicleSpecification.WltpType.valueOf(entity.getWltpType()),
                entity.getVariantName(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
