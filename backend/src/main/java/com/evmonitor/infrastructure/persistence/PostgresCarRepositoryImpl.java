package com.evmonitor.infrastructure.persistence;

import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.CarRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PostgresCarRepositoryImpl implements CarRepository {

    private final JpaCarRepository jpaCarRepository;
    private final JpaVehicleSpecificationRepository jpaVehicleSpecificationRepository;

    @Override
    public Car save(Car car) {
        CarEntity entity = toEntity(car);
        CarEntity savedEntity = jpaCarRepository.save(entity);
        return toDomain(savedEntity);
    }

    @Override
    public Optional<Car> findById(UUID id) {
        return jpaCarRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Car> findAllByUserId(UUID userId) {
        List<CarEntity> entities = jpaCarRepository.findAllByUserId(userId);
        return toDomainList(entities);
    }

    @Override
    public long countByUserId(UUID userId) {
        return jpaCarRepository.countByUserId(userId);
    }

    @Override
    public List<Car> findAllByModel(CarBrand.CarModel model) {
        return toDomainList(jpaCarRepository.findAllByModel(model));
    }

    @Override
    public void deleteById(UUID id) {
        jpaCarRepository.deleteById(id);
    }

    @Override
    public List<Car> findCarsNeedingSohDetection() {
        return toDomainList(jpaCarRepository.findCarsNeedingSohDetection());
    }

    @Override
    public List<Car> findAllByVehicleSpecificationId(UUID vehicleSpecificationId) {
        return toDomainList(jpaCarRepository.findAllByVehicleSpecificationId(vehicleSpecificationId));
    }

    private CarEntity toEntity(Car domain) {
        CarEntity entity = new CarEntity();
        entity.setId(domain.getId());
        entity.setUserId(domain.getUserId());
        entity.setModel(domain.getModel());
        entity.setYear(domain.getYear());
        entity.setLicensePlate(domain.getLicensePlate());
        entity.setTrim(domain.getTrim());
        entity.setBatteryCapacityKwh(domain.getBatteryCapacityKwh());
        entity.setPowerKw(domain.getPowerKw());
        entity.setRegistrationDate(domain.getRegistrationDate());
        entity.setDeregistrationDate(domain.getDeregistrationDate());
        entity.setStatus(domain.getStatus());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());
        entity.setImagePath(domain.getImagePath());
        entity.setImagePublic(domain.isImagePublic());
        entity.setPrimary(domain.isPrimary());
        entity.setBatteryDegradationPercent(domain.getBatteryDegradationPercent());
        entity.setBusinessCar(domain.isBusinessCar());
        entity.setHeatPump(domain.isHeatPump());
        entity.setVehicleSpecificationId(domain.getVehicleSpecificationId());
        return entity;
    }

    /** Single-entity path: one extra query acceptable (used for save/findById). */
    private Car toDomain(CarEntity entity) {
        BigDecimal specNetKwh = null;
        if (entity.getVehicleSpecificationId() != null) {
            specNetKwh = jpaVehicleSpecificationRepository
                    .findById(entity.getVehicleSpecificationId())
                    .map(VehicleSpecificationEntity::getNetBatteryCapacityKwh)
                    .orElse(null);
        }
        return toDomain(entity, specNetKwh);
    }

    /** Batch path: single IN-query for all spec IDs, then map without extra round-trips. */
    private List<Car> toDomainList(List<CarEntity> entities) {
        Set<UUID> specIds = entities.stream()
                .map(CarEntity::getVehicleSpecificationId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, BigDecimal> netKwhById = specIds.isEmpty() ? Map.of()
                : jpaVehicleSpecificationRepository.findAllById(specIds).stream()
                        .filter(s -> s.getNetBatteryCapacityKwh() != null)
                        .collect(Collectors.toMap(
                                VehicleSpecificationEntity::getId,
                                VehicleSpecificationEntity::getNetBatteryCapacityKwh));
        return entities.stream()
                .map(e -> toDomain(e, e.getVehicleSpecificationId() != null
                        ? netKwhById.get(e.getVehicleSpecificationId())
                        : null))
                .toList();
    }

    private Car toDomain(CarEntity entity, BigDecimal specNetKwh) {
        return Car.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .model(entity.getModel())
                .year(entity.getYear())
                .licensePlate(entity.getLicensePlate())
                .trim(entity.getTrim())
                .batteryCapacityKwh(entity.getBatteryCapacityKwh())
                .powerKw(entity.getPowerKw())
                .registrationDate(entity.getRegistrationDate())
                .deregistrationDate(entity.getDeregistrationDate())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .imagePath(entity.getImagePath())
                .imagePublic(entity.isImagePublic())
                .primary(entity.isPrimary())
                .batteryDegradationPercent(entity.getBatteryDegradationPercent())
                .businessCar(entity.isBusinessCar())
                .heatPump(entity.isHeatPump())
                .vehicleSpecificationId(entity.getVehicleSpecificationId())
                .specNetBatteryCapacityKwh(specNetKwh)
                .build();
    }
}
