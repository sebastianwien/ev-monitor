package com.evmonitor.infrastructure.persistence;

import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.CarRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PostgresCarRepositoryImpl implements CarRepository {

    private final JpaCarRepository jpaCarRepository;

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
        return jpaCarRepository.findAllByUserId(userId)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public long countByUserId(UUID userId) {
        return jpaCarRepository.countByUserId(userId);
    }

    @Override
    public List<Car> findAllByModel(CarBrand.CarModel model) {
        return jpaCarRepository.findAllByModel(model).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public void deleteById(UUID id) {
        jpaCarRepository.deleteById(id);
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
        return entity;
    }

    private Car toDomain(CarEntity entity) {
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
                .build();
    }
}
