package com.evmonitor.infrastructure.persistence;

import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class PostgresCarRepositoryImpl implements CarRepository {

    private final JpaCarRepository jpaCarRepository;

    public PostgresCarRepositoryImpl(JpaCarRepository jpaCarRepository) {
        this.jpaCarRepository = jpaCarRepository;
    }

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
                .collect(Collectors.toList());
    }

    @Override
    public long countByUserId(UUID userId) {
        return jpaCarRepository.countByUserId(userId);
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
        return entity;
    }

    private Car toDomain(CarEntity entity) {
        return new Car(
                entity.getId(),
                entity.getUserId(),
                entity.getModel(),
                entity.getYear(),
                entity.getLicensePlate(),
                entity.getTrim(),
                entity.getBatteryCapacityKwh(),
                entity.getPowerKw(),
                entity.getRegistrationDate(),
                entity.getDeregistrationDate(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
