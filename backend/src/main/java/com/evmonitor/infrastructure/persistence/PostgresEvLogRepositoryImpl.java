package com.evmonitor.infrastructure.persistence;

import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.EvLogRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class PostgresEvLogRepositoryImpl implements EvLogRepository {

    private final JpaEvLogRepository jpaRepository;

    public PostgresEvLogRepositoryImpl(JpaEvLogRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public EvLog save(EvLog evLog) {
        EvLogEntity entity = toEntity(evLog);
        EvLogEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<EvLog> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<EvLog> findByIdAndCarId(UUID id, UUID carId) {
        return jpaRepository.findByIdAndCarId(id, carId).map(this::toDomain);
    }

    @Override
    public List<EvLog> findAll() {
        return jpaRepository.findAll().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<EvLog> findAllByCarId(UUID carId) {
        return jpaRepository.findAllByCarId(carId).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<EvLog> findAllByUserId(UUID userId) {
        return jpaRepository.findAllByUserId(userId).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public boolean existsByCarIdAndLoggedAtBetween(UUID carId, LocalDateTime start, LocalDateTime end) {
        return jpaRepository.existsByCarIdAndLoggedAtBetween(carId, start, end);
    }

    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }

    private EvLogEntity toEntity(EvLog domain) {
        return new EvLogEntity(
                domain.getId(),
                domain.getCarId(),
                domain.getKwhCharged(),
                domain.getCostEur(),
                domain.getChargeDurationMinutes(),
                domain.getGeohash(),
                domain.getOdometerKm(),
                domain.getMaxChargingPowerKw(),
                domain.getLoggedAt(),
                domain.getDataSource(),
                domain.isIncludeInStatistics(),
                domain.getCreatedAt(),
                domain.getUpdatedAt());
    }

    private EvLog toDomain(EvLogEntity entity) {
        return new EvLog(
                entity.getId(),
                entity.getCarId(),
                entity.getKwhCharged(),
                entity.getCostEur(),
                entity.getChargeDurationMinutes(),
                entity.getGeohash(),
                entity.getOdometerKm(),
                entity.getMaxChargingPowerKw(),
                entity.getLoggedAt(),
                entity.getDataSource(),
                entity.isIncludeInStatistics(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
