package com.evmonitor.infrastructure.persistence;

import com.evmonitor.domain.DataSource;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.EvLogRepository;
import org.springframework.data.domain.PageRequest;
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
    public List<EvLog> findAllByCarIds(List<UUID> carIds) {
        if (carIds.isEmpty()) return List.of();
        return jpaRepository.findAllByCarIdIn(carIds).stream()
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
    public boolean existsByCarIdAndLoggedAtAndDataSource(UUID carId, LocalDateTime loggedAt, DataSource dataSource) {
        return jpaRepository.existsByCarIdAndLoggedAtAndDataSource(carId, loggedAt, dataSource.name());
    }

    @Override
    public long countByUserId(UUID userId) {
        return jpaRepository.countByUserId(userId);
    }

    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public boolean updateGeohash(UUID carId, LocalDateTime loggedAt, String geohash) {
        return jpaRepository.findByCarIdAndLoggedAt(carId, loggedAt).map(entity -> {
            entity.setGeohash(geohash);
            jpaRepository.save(entity);
            return true;
        }).orElse(false);
    }

    @Override
    public List<EvLog> findLatestByCarId(UUID carId, int limit, int page) {
        return jpaRepository.findAllByCarIdOrderByLoggedAtDesc(carId, PageRequest.of(page, limit))
                .stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<EvLog> findAllWithGeohashAndNoTemperature() {
        return jpaRepository.findAllWithGeohashAndNoTemperature().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void updateTemperature(UUID id, Double temperatureCelsius) {
        jpaRepository.findById(id).ifPresent(entity -> {
            entity.setTemperatureCelsius(temperatureCelsius);
            jpaRepository.save(entity);
        });
    }

    private EvLogEntity toEntity(EvLog domain) {
        EvLogEntity entity = new EvLogEntity(
                domain.getId(),
                domain.getCarId(),
                domain.getKwhCharged(),
                domain.getCostEur(),
                domain.getChargeDurationMinutes(),
                domain.getGeohash(),
                domain.getOdometerKm(),
                domain.getMaxChargingPowerKw(),
                domain.getSocAfterChargePercent(),
                domain.getLoggedAt(),
                domain.getDataSource().name(),
                domain.isIncludeInStatistics(),
                domain.getCreatedAt(),
                domain.getUpdatedAt());
        entity.setSocBeforeChargePercent(domain.getSocBeforeChargePercent());
        entity.setOdometerSuggestionMinKm(domain.getOdometerSuggestionMinKm());
        entity.setOdometerSuggestionMaxKm(domain.getOdometerSuggestionMaxKm());
        entity.setTemperatureCelsius(domain.getTemperatureCelsius());
        return entity;
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
                entity.getSocAfterChargePercent(),
                entity.getSocBeforeChargePercent(),
                entity.getLoggedAt(),
                DataSource.valueOf(entity.getDataSource()),
                entity.isIncludeInStatistics(),
                entity.getOdometerSuggestionMinKm(),
                entity.getOdometerSuggestionMaxKm(),
                entity.getTemperatureCelsius(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
