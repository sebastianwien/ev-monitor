package com.evmonitor.application;

import ch.hsr.geohash.GeoHash;
import com.evmonitor.application.consumption.ConsumptionCalculationService;
import com.evmonitor.domain.*;
import com.evmonitor.domain.weather.TemperatureEnricher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/**
 * CRUD service for charging logs. Ownership checks, creation, update, delete.
 * Statistics and community aggregates live in EvLogStatisticsService.
 * Pure calculation logic lives in ConsumptionCalculationService.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EvLogService {

    private static final int SUPERSEDE_WINDOW_MINUTES = 15;
    private static final BigDecimal SUPERSEDE_KWH_TOLERANCE = new BigDecimal("0.15");

    private final EvLogRepository evLogRepository;
    private final CarRepository carRepository;
    private final CoinLogService coinLogService;
    private final TemperatureEnricher temperatureEnricher;
    private final PlausibilityProperties plausibility;
    private final ConsumptionCalculationService calculationService;

    @Transactional
    public EvLogCreateResponse logCharging(UUID userId, EvLogRequest request) {
        Car car = carRepository.findById(request.carId())
                .orElseThrow(() -> new IllegalArgumentException("Car not found"));

        if (!car.getUserId().equals(userId)) {
            throw new IllegalArgumentException("User does not own the specified car");
        }

        // Convert lat/lon to geohash for privacy. Public chargers get 7-char (~150m), private get 6-char (~600m).
        // Lat/lon are never stored, only the anonymized geohash.
        String geohash = null;
        if (request.latitude() != null && request.longitude() != null) {
            int precision = Boolean.TRUE.equals(request.isPublicCharging()) ? 7 : 6;
            geohash = GeoHash.withCharacterPrecision(request.latitude(), request.longitude(), precision).toBase32();
        }

        EvLog newLog = EvLog.createNew(
                request.carId(),
                request.kwhCharged(),
                request.costEur(),
                request.chargeDurationMinutes(),
                geohash,
                request.odometerKm(),
                request.maxChargingPowerKw(),
                request.socAfterChargePercent(),
                request.loggedAt(),
                request.chargingType(),
                request.routeType(),
                request.tireType(),
                Boolean.TRUE.equals(request.isPublicCharging()),
                request.cpoName());

        // Attach original currency metadata if provided (non-EUR entry)
        if (request.costCurrency() != null && request.costExchangeRate() != null) {
            newLog = newLog.toBuilder()
                    .costExchangeRate(request.costExchangeRate())
                    .costCurrency(request.costCurrency())
                    .build();
        }

        EvLog savedLog = evLogRepository.save(newLog);

        // Suppress any matching import logs (e.g. Tesla auto-import of the same session)
        suppressDuplicateImports(savedLog, userId);

        // Async: enrich with temperature from Open-Meteo (fire-and-forget, nullable result)
        temperatureEnricher.enrichLog(savedLog.getId(), savedLog.getGeohash(), savedLog.getLoggedAt());

        // Award coins for this log entry. CoinEvent determines first vs. subsequent, with optional OCR bonus.
        // First-time detection is via coin history (immutable), not log count — prevents delete-and-recreate farming.
        // NOTE: ocrUsed is client-supplied and not server-verifiable — the +2 bonus is accepted risk
        // (low value, requires conscious manipulation, not worth server-side OCR session tracking).
        CoinLogService.CoinEvent coinEvent;
        if (Boolean.TRUE.equals(request.ocrUsed())) {
            boolean firstOcrEver = !coinLogService.hasEverReceivedCoinForAction(
                    userId, CoinLogService.CoinEvent.MANUAL_LOG_FIRST_OCR.getDescription());
            coinEvent = firstOcrEver
                    ? CoinLogService.CoinEvent.MANUAL_LOG_FIRST_OCR
                    : CoinLogService.CoinEvent.MANUAL_LOG_OCR;
        } else {
            boolean firstLogEver = !coinLogService.hasEverReceivedCoinForAction(
                    userId, CoinLogService.CoinEvent.MANUAL_LOG_FIRST.getDescription());
            coinEvent = firstLogEver
                    ? CoinLogService.CoinEvent.MANUAL_LOG_FIRST
                    : CoinLogService.CoinEvent.MANUAL_LOG_SUBSEQUENT;
        }
        int coinsAwarded = coinLogService.awardCoinsForEvent(userId, coinEvent, savedLog.getId());

        return new EvLogCreateResponse(EvLogResponse.fromDomain(savedLog), coinsAwarded);
    }

    /**
     * Creates a charging log on behalf of a user from an OCPP wallbox session.
     * Called by the internal Wallbox Service — not user-facing.
     */
    @Transactional
    public EvLogResponse createWallboxLog(InternalEvLogRequest request) {
        Car car = carRepository.findById(request.carId())
                .orElseThrow(() -> new IllegalArgumentException("Car not found"));

        if (!car.getUserId().equals(request.userId())) {
            throw new IllegalArgumentException("Car does not belong to user");
        }

        DataSource source = DataSource.WALLBOX_OCPP;
        if (request.dataSource() != null) {
            try { source = DataSource.valueOf(request.dataSource()); } catch (IllegalArgumentException ignored) {}
        }

        // Idempotent: skip if already imported (same car + timestamp + data source).
        // loggedAt wird im EvLog-Konstruktor auf Minuten truncated, daher exakter Match sicher.
        LocalDateTime loggedAtTruncated = request.loggedAt() != null ? request.loggedAt().withSecond(0).withNano(0) : LocalDateTime.now().withSecond(0).withNano(0);
        if (evLogRepository.existsByCarIdAndLoggedAtAndDataSource(request.carId(), loggedAtTruncated, source)) {
            return null;
        }

        ChargingType chargingType = ChargingType.UNKNOWN;
        if (request.chargingType() != null) {
            try { chargingType = ChargingType.valueOf(request.chargingType()); } catch (IllegalArgumentException ignored) {}
        }

        EvLog newLog = EvLog.createFromInternal(
                request.carId(),
                request.kwhCharged(),
                request.chargeDurationMinutes(),
                request.geohash(),
                request.loggedAt(),
                request.odometerSuggestionMinKm(),
                request.odometerSuggestionMaxKm(),
                source,
                request.costEur(),
                chargingType,
                request.odometerKm(),
                request.socBefore(),
                request.socAfter(),
                request.temperatureCelsius());

        EvLog savedLog = evLogRepository.save(newLog);

        // If a USER_LOGGED entry already covers this session, mark this import as superseded immediately
        LocalDateTime from = savedLog.getLoggedAt().minusMinutes(SUPERSEDE_WINDOW_MINUTES);
        LocalDateTime to   = savedLog.getLoggedAt().plusMinutes(SUPERSEDE_WINDOW_MINUTES);
        BigDecimal kwhMin  = savedLog.getKwhCharged().multiply(BigDecimal.ONE.subtract(SUPERSEDE_KWH_TOLERANCE));
        BigDecimal kwhMax  = savedLog.getKwhCharged().multiply(BigDecimal.ONE.add(SUPERSEDE_KWH_TOLERANCE));
        boolean isSuperseded = evLogRepository.findUserLoggedInTimeWindow(savedLog.getCarId(), from, to, kwhMin, kwhMax)
                .stream()
                .min(Comparator.comparing(userLog ->
                        savedLog.getKwhCharged().subtract(userLog.getKwhCharged()).abs()))
                .map(userLog -> { evLogRepository.markAsSuperseded(savedLog.getId(), userLog.getId()); return true; })
                .orElse(false);

        // Award per-log coins — only if this import was NOT immediately superseded by a manual entry.
        // go-eCharger and plain OCPP wallbox coins are TBD and intentionally not awarded here yet.
        if (!isSuperseded && (source == DataSource.TESLA_FLEET_IMPORT || source == DataSource.TESLA_LIVE)) {
            coinLogService.awardCoinsForEvent(request.userId(), CoinLogService.CoinEvent.TESLA_DAILY_LOG, savedLog.getId());
        }

        return EvLogResponse.fromDomain(savedLog);
    }

    private void suppressDuplicateImports(EvLog userLog, UUID userId) {
        LocalDateTime from = userLog.getLoggedAt().minusMinutes(SUPERSEDE_WINDOW_MINUTES);
        LocalDateTime to   = userLog.getLoggedAt().plusMinutes(SUPERSEDE_WINDOW_MINUTES);
        BigDecimal kwhMin  = userLog.getKwhCharged().multiply(BigDecimal.ONE.subtract(SUPERSEDE_KWH_TOLERANCE));
        BigDecimal kwhMax  = userLog.getKwhCharged().multiply(BigDecimal.ONE.add(SUPERSEDE_KWH_TOLERANCE));
        evLogRepository.findImportLogsInTimeWindow(userLog.getCarId(), from, to, kwhMin, kwhMax)
                .forEach(imp -> {
                    evLogRepository.markAsSuperseded(imp.getId(), userLog.getId());
                    // Revert any coins awarded for this import so the user doesn't double-dip
                    int coinSum = coinLogService.sumCoinsForSourceEntity(imp.getId());
                    if (coinSum > 0) {
                        coinLogService.awardCoins(userId, CoinType.ACHIEVEMENT_COIN, -coinSum,
                                CoinLogService.CoinEvent.IMPORT_SUPERSEDED_DEDUCTION.getDescription(),
                                imp.getId());
                    }
                });
    }

    /**
     * Prüft ob ein User das angegebene Fahrzeug besitzt.
     * Wirft IllegalArgumentException bei Ownership-Verletzung (404-equivalent).
     */
    public void verifyCarOwnership(UUID carId, UUID userId) {
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new IllegalArgumentException("Car not found"));
        if (!car.getUserId().equals(userId)) {
            throw new IllegalArgumentException("User does not own the specified car");
        }
    }

    @Transactional
    public void updateGeohash(UUID carId, UUID userId, LocalDateTime loggedAt, String geohash) {
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new IllegalArgumentException("Car not found"));
        if (!car.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Car does not belong to user");
        }
        evLogRepository.updateGeohash(carId, loggedAt, geohash);
    }

    public List<EvLogResponse> getStandaloneLogsForUser(UUID userId) {
        return evLogRepository.findAllByUserId(userId).stream()
                .map(EvLogResponse::fromDomain)
                .toList();
    }

    public EvLogResponse getLogByIdForUser(UUID id, UUID userId) {
        EvLog log = evLogRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Log not found with ID: " + id));

        Car car = carRepository.findById(log.getCarId())
                .orElseThrow(() -> new IllegalArgumentException("Associated car not found"));

        if (!car.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Log not found for current user (ownership mismatch).");
        }

        return EvLogResponse.fromDomain(log);
    }

    @Transactional
    public EvLogResponse updateLog(UUID id, UUID userId, EvLogUpdateRequest request) {
        EvLog existing = evLogRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Log not found with ID: " + id));

        Car car = carRepository.findById(existing.getCarId())
                .orElseThrow(() -> new IllegalArgumentException("Associated car not found"));

        if (!car.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Log not found for current user (ownership mismatch).");
        }

        // Compute new geohash from lat/lon if provided; otherwise keep existing.
        // Precision depends on isPublicCharging (new value if provided, else existing).
        boolean updatedIsPublicCharging = request.isPublicCharging() != null
                ? request.isPublicCharging()
                : existing.isPublicCharging();
        String geohash = existing.getGeohash();
        boolean geohashChanged = false;
        if (request.latitude() != null && request.longitude() != null) {
            int precision = updatedIsPublicCharging ? 7 : 6;
            geohash = GeoHash.withCharacterPrecision(request.latitude(), request.longitude(), precision).toBase32();
            geohashChanged = !geohash.equals(existing.getGeohash());
        } else if (!updatedIsPublicCharging && geohash != null && geohash.length() > 6) {
            // Privacy: if switched from public→private without new coordinates, truncate to 6-char precision.
            geohash = geohash.substring(0, 6);
            geohashChanged = true;
        }

        EvLog updated = existing.toBuilder()
                .kwhCharged(request.kwhCharged()             != null ? request.kwhCharged()             : existing.getKwhCharged())
                .costEur(request.costEur()                   != null ? request.costEur()                : existing.getCostEur())
                .chargeDurationMinutes(request.chargeDurationMinutes() != null ? request.chargeDurationMinutes() : existing.getChargeDurationMinutes())
                .geohash(geohash)
                .odometerKm(request.odometerKm()             != null ? request.odometerKm()             : existing.getOdometerKm())
                .maxChargingPowerKw(request.maxChargingPowerKw() != null ? request.maxChargingPowerKw() : existing.getMaxChargingPowerKw())
                .socAfterChargePercent(request.socAfterChargePercent() != null ? request.socAfterChargePercent() : existing.getSocAfterChargePercent())
                .socBeforeChargePercent(request.socBeforeChargePercent() != null ? request.socBeforeChargePercent() : existing.getSocBeforeChargePercent())
                .loggedAt(request.loggedAt()                 != null ? request.loggedAt()               : existing.getLoggedAt())
                .chargingType(request.chargingType()         != null ? request.chargingType()            : existing.getChargingType())
                .routeType(request.routeType()               != null ? request.routeType()               : existing.getRouteType())
                .tireType(request.tireType()                 != null ? request.tireType()                : existing.getTireType())
                .publicCharging(updatedIsPublicCharging)
                .cpoName(request.cpoName()                   != null ? request.cpoName()                 : existing.getCpoName())
                .costExchangeRate(request.costExchangeRate() != null ? request.costExchangeRate()     : existing.getCostExchangeRate())
                .costCurrency(request.costCurrency()         != null ? request.costCurrency()          : existing.getCostCurrency())
                .updatedAt(LocalDateTime.now())
                .build();

        EvLog savedLog = evLogRepository.save(updated);

        // Async: re-enrich temperature if location was added/changed
        if (geohashChanged || (savedLog.getGeohash() != null && savedLog.getTemperatureCelsius() == null)) {
            temperatureEnricher.enrichLog(savedLog.getId(), savedLog.getGeohash(), savedLog.getLoggedAt());
        }

        return EvLogResponse.fromDomain(savedLog);
    }

    @Transactional
    public void deleteLog(UUID id, UUID userId) {
        EvLog log = evLogRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Log not found with ID: " + id));

        Car car = carRepository.findById(log.getCarId())
                .orElseThrow(() -> new IllegalArgumentException("Associated car not found"));

        if (!car.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Log not found for current user (ownership mismatch).");
        }

        // Deduct coins that were awarded for this log (identified via source_entity_id).
        // Only deduct if coins were actually awarded — prevents creating negative phantom entries.
        int coinSum = coinLogService.sumCoinsForSourceEntity(id);
        if (coinSum > 0) {
            coinLogService.awardCoins(userId, CoinType.ACHIEVEMENT_COIN, -coinSum,
                    CoinLogService.CoinEvent.LOG_DELETED_DEDUCTION.getDescription(), id);
        }

        // Before deleting, clear any superseded_by references so suppressed imports resurface.
        // The DB FK uses ON DELETE SET NULL in production, but this ensures it also works in tests (H2).
        evLogRepository.clearSupersededByReferences(id);
        evLogRepository.deleteById(id);
    }

    public List<EvLogResponse> getLogsForCar(UUID carId, UUID userId) {
        return getLogsForCar(carId, userId, null);
    }

    public List<EvLogResponse> getLogsForCar(UUID carId, UUID userId, Integer limit) {
        return getLogsForCar(carId, userId, limit, 0);
    }

    @Transactional(readOnly = true)
    public List<EvLogResponse> getLogsForCar(UUID carId, UUID userId, Integer limit, int page) {
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new IllegalArgumentException("Car not found"));

        if (!car.getUserId().equals(userId)) {
            throw new IllegalArgumentException("User does not own the specified car");
        }

        // All logs sorted ascending — needed for consumption context (logX lookups)
        List<EvLog> allLogsSorted = evLogRepository.findAllByCarId(carId).stream()
                .sorted(Comparator.comparing(EvLog::getLoggedAt))
                .toList();

        // Compute per-log consumption + plausibility on the full dataset (SoC-based)
        Map<UUID, ConsumptionResult> consumptionByLog = new LinkedHashMap<>(car.getBatteryCapacityKwh() != null
                ? calculationService.calculateConsumptionPerLog(allLogsSorted, calculationService.buildCapacityLookup(car), calculationService.lookupWltp(car))
                : Map.of());

        // Distance since last charge — covers logs with odometer regardless of SoC availability
        Map<UUID, Integer> distanceByLogId = calculationService.computeDistanceByLogId(allLogsSorted);

        // Fallback: for logs with distance but no SoC-based consumption, estimate via kWh_charged/distance.
        // Marked as estimated=true so the frontend can display it differently (e.g. "~16.35 kWh/100km").
        // Less accurate than SoC-based (kWh_charged ≠ kWh_consumed), but useful when no SoC is available.
        for (EvLog log : allLogsSorted) {
            if (consumptionByLog.containsKey(log.getId())) continue;
            Integer dist = distanceByLogId.get(log.getId());
            if (dist == null || dist < plausibility.getMinTripDistanceKm()) continue;
            double c = calculationService.effectiveKwhForConsumption(log).doubleValue() / dist * 100.0;
            boolean plausible = c >= plausibility.getAbsoluteMinKwhPer100km() && c <= plausibility.getAbsoluteMaxKwhPer100km();
            consumptionByLog.put(log.getId(), new ConsumptionResult(
                    BigDecimal.valueOf(c).setScale(2, RoundingMode.HALF_UP), plausible, dist, true));
        }

        // Return the requested page, enriched with consumption and distance data.
        List<EvLog> page_logs = (limit != null && limit > 0)
                ? evLogRepository.findLatestByCarId(carId, limit, page)
                : allLogsSorted.reversed();

        return page_logs.stream()
                .map(log -> EvLogResponse.fromDomain(log, consumptionByLog.get(log.getId()), distanceByLogId.get(log.getId())))
                .toList();
    }

    /**
     * Toggles include_in_statistics for a single log. Ownership is verified.
     */
    @Transactional
    public EvLogResponse updateIncludeInStatistics(UUID id, UUID userId, boolean includeInStatistics) {
        EvLog log = evLogRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Log not found"));
        Car car = carRepository.findById(log.getCarId())
                .orElseThrow(() -> new IllegalArgumentException("Associated car not found"));
        if (!car.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Log not found for current user (ownership mismatch).");
        }
        EvLog updated = log.withIncludeInStatistics(includeInStatistics);
        EvLog saved = evLogRepository.save(updated);
        return EvLogResponse.fromDomain(saved);
    }

    /**
     * Weist einen einzelnen (ungruppierten) Log einem anderen Fahrzeug zu.
     * Beide Fahrzeuge müssen dem gleichen User gehören.
     */
    @Transactional
    public void reassignLog(UUID logId, UUID targetCarId, UUID userId) {
        EvLog log = evLogRepository.findById(logId)
                .orElseThrow(() -> new IllegalArgumentException("Log not found"));

        Car sourceCar = carRepository.findById(log.getCarId())
                .orElseThrow(() -> new IllegalArgumentException("Source car not found"));
        if (!sourceCar.getUserId().equals(userId)) {
            throw new IllegalArgumentException("User does not own this log");
        }

        Car targetCar = carRepository.findById(targetCarId)
                .orElseThrow(() -> new IllegalArgumentException("Target car not found"));
        if (!targetCar.getUserId().equals(userId)) {
            throw new IllegalArgumentException("User does not own the target car");
        }

        evLogRepository.updateCarIdForLog(logId, targetCarId);
    }

}
