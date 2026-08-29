package com.evmonitor.infrastructure.persistence;

import com.evmonitor.domain.ChargingType;
import com.evmonitor.domain.DataSource;
import com.evmonitor.domain.EnergyMeasurementType;
import com.evmonitor.domain.EnergySource;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.weather.TemperatureSource;
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
        // Bestehende Zeile laden statt eine frische Entity zu bauen: Spalten, die das Domain-
        // Modell nicht kennt (power_curve_points, telemetry_extras), fielen sonst bei jedem
        // Update auf NULL zurueck. Kostet keinen zusaetzlichen Roundtrip - JPA laedt die Zeile
        // beim merge einer detached Entity ohnehin.
        EvLogEntity entity = evLog.getId() != null
                ? jpaRepository.findById(evLog.getId()).orElseGet(EvLogEntity::new)
                : new EvLogEntity();
        applyDomain(entity, evLog);
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
    public List<EvLog> findSohCandidateLogs(UUID carId, int minSocHub, int limit) {
        return jpaRepository.findSohCandidateLogs(carId, minSocHub, PageRequest.of(0, limit)).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public java.math.BigDecimal findLargestSocHub(UUID carId) {
        return jpaRepository.findLargestSocHub(carId);
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
    public List<EvLog> findChargeMatchCandidates(UUID carId, Integer odoMin, Integer odoMax,
                                                  LocalDateTime timeMin, LocalDateTime timeMax) {
        // JpaQuery erwartet :odoKm als Disable-Flag (null = Odo-Filter aus).
        Integer odoFilterFlag = (odoMin == null && odoMax == null) ? null : 1;
        return jpaRepository.findChargeMatchCandidates(
                carId, timeMin, timeMax, odoFilterFlag, odoMin, odoMax).stream()
                .map(this::toDomain)
                .toList();
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
    @Transactional
    public void deleteAllByUserIdAndDataSource(UUID userId, DataSource dataSource) {
        jpaRepository.deleteAllByUserIdAndDataSource(userId, dataSource.name());
    }

    @Override
    @Transactional
    public void deleteAllByUserIdAndDataSourceIn(UUID userId, List<DataSource> dataSources) {
        List<String> names = dataSources.stream().map(DataSource::name).toList();
        jpaRepository.deleteAllByUserIdAndDataSourceIn(userId, names);
    }

    @Override
    public int countByUserIdAndDataSource(UUID userId, DataSource dataSource) {
        return jpaRepository.countByUserIdAndDataSource(userId, dataSource.name());
    }

    @Override
    @Transactional
    public void updateTelemetryExtras(UUID carId, LocalDateTime loggedAt, String telemetryExtrasJson) {
        // @Modifying-Query benoetigt eine Transaktion. Wenn Caller (z.B.
        // XpengImportService.runImport) keine eigene tx aufspannt, fliegt
        // sonst TransactionRequiredException. updatePowerCurvePoints +
        // updateTelemetryExtrasById sind aus demselben Grund @Transactional.
        jpaRepository.updateTelemetryExtras(carId, loggedAt, telemetryExtrasJson);
    }

    @Override
    @Transactional
    public void updateTelemetryExtrasById(UUID logId, String telemetryExtrasJson) {
        jpaRepository.updateTelemetryExtrasById(logId, telemetryExtrasJson);
    }

    @Override
    @Transactional
    public void updatePowerCurvePoints(UUID id, String powerCurvePointsJson) {
        jpaRepository.updatePowerCurvePoints(id, powerCurvePointsJson);
    }

    @Override
    public Optional<String> findPowerCurvePointsJson(UUID id) {
        return jpaRepository.findById(id).map(EvLogEntity::getPowerCurvePoints);
    }

    @Override
    public Optional<PowerCurveLookup> findOwnerIdAndPowerCurveJson(UUID logId) {
        return jpaRepository.findOwnerIdAndPowerCurveJson(logId)
                .map(row -> new PowerCurveLookup(
                        row.getOwnerUserId(), row.getPowerCurveJson(), row.getSocCurveJson()));
    }

    @Override
    @Transactional
    public void updateSocCurvePoints(UUID id, String socCurvePointsJson) {
        jpaRepository.updateSocCurvePoints(id, socCurvePointsJson);
    }

    @Override
    @Transactional
    public boolean setShareToken(UUID logId, String token, LocalDateTime createdAt) {
        return jpaRepository.updateShareToken(logId, token, createdAt) > 0;
    }

    @Override
    @Transactional
    public void clearShareToken(UUID logId) {
        jpaRepository.clearShareToken(logId);
    }

    @Override
    public Optional<String> findShareToken(UUID logId) {
        return jpaRepository.findShareToken(logId);
    }

    @Override
    public Optional<PublicCurveLookup> findPublicCurveByShareToken(String token) {
        return jpaRepository.findPublicCurveByShareToken(token)
                .map(r -> new PublicCurveLookup(
                        r.getPowerCurveJson(),
                        r.getCarModel(),
                        r.getKwhCharged(),
                        r.getKwhAtVehicle(),
                        r.getChargeDurationMinutes(),
                        r.getSocBefore(),
                        r.getSocAfter(),
                        r.getMaxChargingPowerKw(),
                        r.getCpoName(),
                        Boolean.TRUE.equals(r.getPublicCharging()),
                        r.getChargingType(),
                        r.getLoggedAt()));
    }

    @Override
    public Optional<EvLog> updateGeohash(UUID carId, LocalDateTime loggedAt, String geohash) {
        return jpaRepository.findByCarIdAndLoggedAt(carId, loggedAt).map(entity -> {
            entity.setGeohash(geohash);
            return toDomain(jpaRepository.save(entity));
        });
    }

    @Override
    public List<EvLog> findLatestByCarId(UUID carId, int limit, int page) {
        return jpaRepository.findAllByCarIdOrderByLoggedAtDesc(carId, PageRequest.of(page, limit))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<EvLog> findPagedByCarId(UUID carId, LocalDateTime from, LocalDateTime to, int size, int offset) {
        return jpaRepository.findPagedByCarId(carId, from, to, PageRequest.of(offset / size, size))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public long countByCarIdAndDateRange(UUID carId, LocalDateTime from, LocalDateTime to) {
        return jpaRepository.countByCarIdAndDateRange(carId, from, to);
    }

    @Override
    public List<EvLog> findPagedByCarIds(List<UUID> carIds, LocalDateTime from, LocalDateTime to, int size, int offset) {
        if (carIds.isEmpty()) return List.of();
        return jpaRepository.findPagedByCarIds(carIds, from, to, PageRequest.of(offset / size, size))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public long countByCarIdsAndDateRange(List<UUID> carIds, LocalDateTime from, LocalDateTime to) {
        if (carIds.isEmpty()) return 0L;
        return jpaRepository.countByCarIdsAndDateRange(carIds, from, to);
    }

    @Override
    public List<TemperatureCandidate> findTemperatureCandidates(int limit) {
        return jpaRepository.findTemperatureCandidates(org.springframework.data.domain.PageRequest.of(0, limit))
                .stream()
                .map(row -> new TemperatureCandidate((UUID) row[0], (String) row[1], (LocalDateTime) row[2]))
                .toList();
    }

    @Override
    @Transactional
    public void updateTemperature(UUID id, Double temperatureCelsius, TemperatureSource source) {
        jpaRepository.updateTemperatureAndSource(id, temperatureCelsius, source);
    }

    @Override
    @Transactional
    public void updateTemperatureSource(UUID id, TemperatureSource source) {
        jpaRepository.updateTemperatureSource(id, source);
    }

    @Override
    public boolean updateTemperatureIfAbsent(UUID carId, LocalDateTime loggedAt, Double temperatureCelsius) {
        return jpaRepository.findByCarIdAndLoggedAt(carId, loggedAt)
                .filter(entity -> entity.getTemperatureCelsius() == null)
                .map(entity -> {
                    entity.setTemperatureCelsius(temperatureCelsius);
                    entity.setTemperatureSource(TemperatureSource.MEASURED);
                    jpaRepository.save(entity);
                    return true;
                })
                .orElse(false);
    }

    @Override
    @Transactional
    public void updateCarIdForLog(UUID logId, UUID targetCarId) {
        jpaRepository.updateCarIdForLog(logId, targetCarId);
    }

    @Override
    public Optional<EvLog> findMostRecentPricedLogAtGeohash(UUID userId, String geohash,
                                                            com.evmonitor.domain.ChargingType chargingType) {
        var results = jpaRepository.findRecentPricedByUserIdAndGeohash(userId, geohashPrefix(geohash),
                chargingType == null ? null : chargingType.name(),
                org.springframework.data.domain.PageRequest.of(0, 1));
        return results.isEmpty() ? Optional.empty() : Optional.of(toDomain(results.get(0)));
    }

    @Override
    public Optional<UUID> findMostRecentChargingProviderAtGeohash(UUID userId, String geohash) {
        var results = jpaRepository.findRecentWithProviderByUserIdAndGeohash(userId, geohashPrefix(geohash),
                org.springframework.data.domain.PageRequest.of(0, 1));
        if (results.isEmpty()) return Optional.empty();
        return Optional.ofNullable(results.get(0).getChargingProviderId());
    }

    @Override
    public Optional<TireType> findMostRecentTireTypeBefore(UUID carId, LocalDateTime before) {
        var results = jpaRepository.findMostRecentTireTypeBefore(carId, before, PageRequest.of(0, 1));
        if (results.isEmpty()) return Optional.empty();
        try {
            return Optional.of(TireType.valueOf(results.get(0)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<RouteType> findMostRecentRouteTypeBefore(UUID carId, LocalDateTime before) {
        var results = jpaRepository.findMostRecentRouteTypeBefore(carId, before, PageRequest.of(0, 1));
        if (results.isEmpty()) return Optional.empty();
        try {
            return Optional.of(RouteType.valueOf(results.get(0)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<EvLog> findPricelessLogsAtGeohash(UUID userId, String geohash) {
        return jpaRepository.findPricelessByUserIdAndGeohash(userId, geohash).stream()
                .map(this::toDomain)
                .toList();
    }

    private static String geohashPrefix(String geohash) {
        assert geohash.length() >= 6 : "geohash must be at least 6 chars for meaningful prefix lookup";
        return geohash.substring(0, Math.min(6, geohash.length())) + "%";
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

    @Override
    public List<EvLog> findEnrichableTeslaLogs(UUID userId, LocalDateTime cutoff) {
        return jpaRepository.findEnrichableTeslaLogs(userId, cutoff, JpaEvLogRepository.TESLA_DATA_SOURCES)
                .stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional
    public int enrichWithTeslaPricing(UUID id, UUID userId, BigDecimal costEur, String cpoName) {
        return jpaRepository.enrichWithTeslaPricing(id, userId, costEur, cpoName, JpaEvLogRepository.TESLA_DATA_SOURCES);
    }

    /**
     * Schreibt die Domain-Felder auf die uebergebene Entity. Bewusst mutierend statt eine neue
     * Entity zu liefern: Spalten ohne Domain-Entsprechung bleiben so erhalten.
     */
    private void applyDomain(EvLogEntity entity, EvLog domain) {
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
        entity.setEnergySource(domain.getEnergySource() != null ? domain.getEnergySource().name() : null);
        entity.setCostExchangeRate(domain.getCostExchangeRate());
        entity.setCostCurrency(domain.getCostCurrency());
        entity.setChargingProviderId(domain.getChargingProviderId());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());
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
                .energySource(entity.getEnergySource() != null ? EnergySource.valueOf(entity.getEnergySource()) : null)
                .costExchangeRate(entity.getCostExchangeRate())
                .costCurrency(entity.getCostCurrency())
                .chargingProviderId(entity.getChargingProviderId())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .hasPowerCurve(entity.getPowerCurvePoints() != null && !entity.getPowerCurvePoints().isBlank())
                .hasSocCurve(entity.getSocCurvePoints() != null && !entity.getSocCurvePoints().isBlank())
                .build();
    }
}
