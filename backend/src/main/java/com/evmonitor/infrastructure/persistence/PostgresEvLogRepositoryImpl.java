package com.evmonitor.infrastructure.persistence;

import com.evmonitor.domain.ChargingType;
import com.evmonitor.domain.DataSource;
import com.evmonitor.domain.EnergyMeasurementType;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.EvLogRepository;
import com.evmonitor.domain.RouteType;
import com.evmonitor.domain.TireType;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class PostgresEvLogRepositoryImpl implements EvLogRepository {

    private final JpaEvLogRepository jpaRepository;

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
                .toList();
    }

    @Override
    public List<EvLog> findAllByCarId(UUID carId) {
        return jpaRepository.findAllByCarId(carId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<EvLog> findRecentAtVehicleLogsWithSoc(UUID carId, int limit) {
        return jpaRepository.findRecentAtVehicleLogsWithSoc(carId, PageRequest.of(0, limit)).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<EvLog> findAllByCarIds(List<UUID> carIds) {
        if (carIds.isEmpty()) return List.of();
        return jpaRepository.findAllByCarIdIn(carIds).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<EvLog> findAllByUserId(UUID userId) {
        return jpaRepository.findAllByUserId(userId).stream()
                .map(this::toDomain)
                .toList();
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
                .toList();
    }

    @Override
    public List<EvLog> findAllWithGeohashAndNoTemperature() {
        return jpaRepository.findAllWithGeohashAndNoTemperature().stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public void updateTemperature(UUID id, Double temperatureCelsius) {
        jpaRepository.findById(id).ifPresent(entity -> {
            entity.setTemperatureCelsius(temperatureCelsius);
            jpaRepository.save(entity);
        });
    }

    @Override
    @Transactional
    public void updateCarIdForLog(UUID logId, UUID targetCarId) {
        jpaRepository.updateCarIdForLog(logId, targetCarId);
    }

    @Override
    public Optional<EvLog> findMostRecentLogAtGeohash(UUID userId, String geohash) {
        var results = jpaRepository.findRecentByUserIdAndGeohash(userId, geohash,
                org.springframework.data.domain.PageRequest.of(0, 1));
        if (results.isEmpty()) return Optional.empty();
        return Optional.of(toDomain(results.get(0)));
    }

    @Override
    public Optional<UUID> findMostRecentChargingProviderAtGeohash(UUID userId, String geohash) {
        var results = jpaRepository.findRecentWithProviderByUserIdAndGeohash(userId, geohash,
                org.springframework.data.domain.PageRequest.of(0, 1));
        if (results.isEmpty()) return Optional.empty();
        return Optional.ofNullable(results.get(0).getChargingProviderId());
    }

    @Override
    public List<com.evmonitor.domain.GeohashPoint> findGeohashDataByCarId(UUID carId) {
        return jpaRepository.findGeohashDataByCarId(carId).stream()
                .map(row -> new com.evmonitor.domain.GeohashPoint((String) row[0], (java.math.BigDecimal) row[1]))
                .toList();
    }

    @Override
    public List<EvLog> findByCarIdAndDateAndKwhChargedAndDataSource(
            UUID carId, java.time.LocalDate date, java.math.BigDecimal kwhCharged, DataSource dataSource) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();
        return jpaRepository.findByCarIdAndDateRangeAndKwhChargedAndDataSource(
                        carId, startOfDay, endOfDay, kwhCharged, dataSource.name())
                .stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional
    public void updateRawImportData(UUID id, String rawJson) {
        jpaRepository.updateRawImportData(id, rawJson);
    }

    @Transactional
    public void updateRouteType(UUID id, RouteType routeType) {
        jpaRepository.updateRouteType(id, routeType.name());
    }

    private EvLogEntity toEntity(EvLog domain) {
        EvLogEntity entity = new EvLogEntity();
        entity.setId(domain.getId());
        entity.setCarId(domain.getCarId());
        entity.setKwhCharged(domain.getKwhCharged());
        entity.setKwhAtVehicle(domain.getKwhAtVehicle());
        entity.setCostEur(domain.getCostEur());
        entity.setChargeDurationMinutes(domain.getChargeDurationMinutes());
        entity.setGeohash(domain.getGeohash());
        entity.setOdometerKm(domain.getOdometerKm());
        entity.setMaxChargingPowerKw(domain.getMaxChargingPowerKw());
        entity.setSocAfterChargePercent(domain.getSocAfterChargePercent());
        entity.setSocBeforeChargePercent(domain.getSocBeforeChargePercent());
        entity.setLoggedAt(domain.getLoggedAt());
        entity.setDataSource(domain.getDataSource().name());
        entity.setIncludeInStatistics(domain.isIncludeInStatistics());
        entity.setOdometerSuggestionMinKm(domain.getOdometerSuggestionMinKm());
        entity.setOdometerSuggestionMaxKm(domain.getOdometerSuggestionMaxKm());
        entity.setTemperatureCelsius(domain.getTemperatureCelsius());
        entity.setChargingType(domain.getChargingType() != null ? domain.getChargingType().name() : null);
        entity.setRawImportData(domain.getRawImportData());
        entity.setRouteType(domain.getRouteType() != null ? domain.getRouteType().name() : null);
        entity.setTireType(domain.getTireType() != null ? domain.getTireType().name() : null);
        entity.setSessionGroupId(domain.getSessionGroupId());
        entity.setPublicCharging(domain.isPublicCharging());
        entity.setCpoName(domain.getCpoName());
        entity.setMeasurementType(domain.getMeasurementType().name());
        entity.setCostExchangeRate(domain.getCostExchangeRate());
        entity.setCostCurrency(domain.getCostCurrency());
        entity.setChargingProviderId(domain.getChargingProviderId());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());
        return entity;
    }

    private EvLog toDomain(EvLogEntity entity) {
        return EvLog.builder()
                .id(entity.getId())
                .carId(entity.getCarId())
                .kwhCharged(entity.getKwhCharged())
                .kwhAtVehicle(entity.getKwhAtVehicle())
                .costEur(entity.getCostEur())
                .chargeDurationMinutes(entity.getChargeDurationMinutes())
                .geohash(entity.getGeohash())
                .odometerKm(entity.getOdometerKm())
                .maxChargingPowerKw(entity.getMaxChargingPowerKw())
                .socAfterChargePercent(entity.getSocAfterChargePercent())
                .socBeforeChargePercent(entity.getSocBeforeChargePercent())
                .loggedAt(entity.getLoggedAt())
                .dataSource(DataSource.valueOf(entity.getDataSource()))
                .includeInStatistics(entity.isIncludeInStatistics())
                .odometerSuggestionMinKm(entity.getOdometerSuggestionMinKm())
                .odometerSuggestionMaxKm(entity.getOdometerSuggestionMaxKm())
                .temperatureCelsius(entity.getTemperatureCelsius())
                .chargingType(entity.getChargingType() != null ? ChargingType.valueOf(entity.getChargingType()) : ChargingType.UNKNOWN)
                .rawImportData(entity.getRawImportData())
                .routeType(entity.getRouteType() != null ? RouteType.valueOf(entity.getRouteType()) : null)
                .tireType(entity.getTireType() != null ? TireType.valueOf(entity.getTireType()) : null)
                .sessionGroupId(entity.getSessionGroupId())
                .publicCharging(entity.isPublicCharging())
                .cpoName(entity.getCpoName())
                .measurementType(entity.getMeasurementType() != null ? EnergyMeasurementType.valueOf(entity.getMeasurementType()) : null)
                .costExchangeRate(entity.getCostExchangeRate())
                .costCurrency(entity.getCostCurrency())
                .chargingProviderId(entity.getChargingProviderId())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
