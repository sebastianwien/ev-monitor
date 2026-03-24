package com.evmonitor.infrastructure.persistence;

import com.evmonitor.domain.ChargingType;
import com.evmonitor.domain.DataSource;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.EvLogRepository;
import com.evmonitor.domain.RouteType;
import com.evmonitor.domain.TireType;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    public boolean existsByCarIdAndOdometerKmAndLoggedAtBetween(UUID carId, Integer odometerKm, LocalDateTime start, LocalDateTime end) {
        return jpaRepository.existsByCarIdAndOdometerKmAndLoggedAtBetween(carId, odometerKm, start, end);
    }

    @Override
    public boolean existsByCarIdAndLoggedAtAndDataSource(UUID carId, LocalDateTime loggedAt, DataSource dataSource) {
        return jpaRepository.existsByCarIdAndLoggedAtAndDataSource(carId, loggedAt, dataSource.name());
    }

    @Override
    public boolean existsByCarIdAndLoggedAtAndKwhCharged(UUID carId, LocalDateTime loggedAt, BigDecimal kwhCharged) {
        return jpaRepository.existsByCarIdAndLoggedAtAndKwhCharged(carId, loggedAt, kwhCharged);
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
    public void deleteAllByUserIdAndDataSource(UUID userId, DataSource dataSource) {
        jpaRepository.deleteAllByUserIdAndDataSource(userId, dataSource.name());
    }

    @Override
    public void deleteAllByUserIdAndDataSourceIn(UUID userId, List<DataSource> dataSources) {
        List<String> names = dataSources.stream().map(DataSource::name).toList();
        jpaRepository.deleteAllByUserIdAndDataSourceIn(userId, names);
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

    @Override
    public List<EvLog> findImportLogsInTimeWindow(UUID carId, LocalDateTime from, LocalDateTime to,
                                                   BigDecimal kwhMin, BigDecimal kwhMax) {
        return jpaRepository.findImportLogsInTimeWindow(carId, from, to, kwhMin, kwhMax).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<EvLog> findUserLoggedInTimeWindow(UUID carId, LocalDateTime from, LocalDateTime to,
                                                  BigDecimal kwhMin, BigDecimal kwhMax) {
        return jpaRepository.findUserLoggedInTimeWindow(carId, from, to, kwhMin, kwhMax).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void markAsSuperseded(UUID id, UUID supersededById) {
        jpaRepository.markAsSuperseded(id, supersededById);
    }

    @Override
    @Transactional
    public void clearSupersededByReferences(UUID supersededById) {
        jpaRepository.clearSupersededByReferences(supersededById);
    }

    @Override
    @Transactional
    public void setSessionGroupId(UUID logId, UUID groupId) {
        jpaRepository.setSessionGroupId(logId, groupId);
    }

    @Override
    public List<EvLog> findAllBySessionGroupId(UUID groupId) {
        return jpaRepository.findAllBySessionGroupId(groupId).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<EvLog> findAllByCarIdExcludingSubSessions(UUID carId) {
        return jpaRepository.findAllByCarIdExcludingSubSessions(carId).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<com.evmonitor.domain.GeohashPoint> findGeohashDataByCarId(UUID carId) {
        return jpaRepository.findGeohashDataByCarId(carId).stream()
                .map(row -> new com.evmonitor.domain.GeohashPoint((String) row[0], (java.math.BigDecimal) row[1]))
                .collect(Collectors.toList());
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
        entity.setChargingType(domain.getChargingType() != null ? domain.getChargingType().name() : null);
        entity.setRawImportData(domain.getRawImportData());
        entity.setRouteType(domain.getRouteType() != null ? domain.getRouteType().name() : null);
        entity.setTireType(domain.getTireType() != null ? domain.getTireType().name() : null);
        entity.setSupersededBy(domain.getSupersededBy());
        entity.setSessionGroupId(domain.getSessionGroupId());
        entity.setPublicCharging(domain.isPublicCharging());
        entity.setCpoName(domain.getCpoName());
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
                entity.getChargingType() != null ? ChargingType.valueOf(entity.getChargingType()) : ChargingType.UNKNOWN,
                entity.getRawImportData(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getRouteType() != null ? RouteType.valueOf(entity.getRouteType()) : null,
                entity.getTireType() != null ? TireType.valueOf(entity.getTireType()) : null,
                entity.getSupersededBy(),
                entity.getSessionGroupId(),
                entity.isPublicCharging(),
                entity.getCpoName());
    }
}
