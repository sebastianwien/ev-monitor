package com.evmonitor.application;

import ch.hsr.geohash.GeoHash;
import com.evmonitor.domain.*;
import com.evmonitor.infrastructure.weather.TemperatureEnrichmentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 *   calculateConsumption()              ← atomare SoC-Formel (ein Log-Paar)
 *     └── aufgerufen von
 *   calculateConsumptionPerLog()        ← zwei-Pass: Rohwerte + Plausibilität
 *     ├── getLogsForCar()               → Dashboard-Log-Liste (pro Log)
 *     ├── getStatistics()               → User-Statistiken
 *     └── getPlausibleEntriesForCar()   ← SoC→Fallback-Kapselung (neu)
 *           ├── calculateCommunityAvgConsumption()   → Public Model Page
 *           └── calculateSeasonalConsumption()       → Saisonal
 */
@Service
public class EvLogService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int SUPERSEDE_WINDOW_MINUTES = 15;
    private static final BigDecimal SUPERSEDE_KWH_TOLERANCE = new BigDecimal("0.15");

    private final EvLogRepository evLogRepository;
    private final CarRepository carRepository;
    private final UserRepository userRepository;
    private final CoinLogService coinLogService;
    private final TemperatureEnrichmentService temperatureEnrichmentService;
    private final VehicleSpecificationRepository vehicleSpecificationRepository;
    private final PlausibilityProperties plausibility;

    public EvLogService(EvLogRepository evLogRepository, CarRepository carRepository, UserRepository userRepository,
            CoinLogService coinLogService, TemperatureEnrichmentService temperatureEnrichmentService,
            VehicleSpecificationRepository vehicleSpecificationRepository, PlausibilityProperties plausibility) {
        this.evLogRepository = evLogRepository;
        this.carRepository = carRepository;
        this.userRepository = userRepository;
        this.coinLogService = coinLogService;
        this.temperatureEnrichmentService = temperatureEnrichmentService;
        this.vehicleSpecificationRepository = vehicleSpecificationRepository;
        this.plausibility = plausibility;
    }

    @Transactional
    public EvLogCreateResponse logCharging(UUID userId, EvLogRequest request) {
        Car car = carRepository.findById(request.carId())
                .orElseThrow(() -> new IllegalArgumentException("Car not found"));

        if (!car.getUserId().equals(userId)) {
            throw new IllegalArgumentException("User does not own the specified car");
        }

        // Convert lat/lon to 5-character geohash for privacy (~5km precision)
        // Lat/lon are never stored, only the anonymized geohash
        String geohash = null;
        if (request.latitude() != null && request.longitude() != null) {
            geohash = GeoHash.withCharacterPrecision(request.latitude(), request.longitude(), 5).toBase32();
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
                request.tireType());

        EvLog savedLog = evLogRepository.save(newLog);

        // Suppress any matching import logs (e.g. Tesla auto-import of the same session)
        suppressDuplicateImports(savedLog, userId);

        // Async: enrich with temperature from Open-Meteo (fire-and-forget, nullable result)
        temperatureEnrichmentService.enrichLog(savedLog.getId(), savedLog.getGeohash(), savedLog.getLoggedAt());

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

        // Idempotent: skip if already imported (same car + timestamp + data source)
        if (evLogRepository.existsByCarIdAndLoggedAtAndDataSource(request.carId(), request.loggedAt(), source)) {
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
                chargingType);

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
        if (!isSuperseded && (source == DataSource.TESLA_FLEET || source == DataSource.TESLA_HOME)) {
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

    @Transactional
    public void updateGeohash(UUID carId, UUID userId, LocalDateTime loggedAt, String geohash) {
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new IllegalArgumentException("Car not found"));
        if (!car.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Car does not belong to user");
        }
        evLogRepository.updateGeohash(carId, loggedAt, geohash);
    }

    public List<EvLogResponse> getAllLogsForUser(UUID userId) {
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

        // Compute new geohash from lat/lon if provided; otherwise keep existing
        String geohash = existing.getGeohash();
        boolean geohashChanged = false;
        if (request.latitude() != null && request.longitude() != null) {
            geohash = GeoHash.withCharacterPrecision(request.latitude(), request.longitude(), 5).toBase32();
            geohashChanged = !geohash.equals(existing.getGeohash());
        }

        EvLog updated = new EvLog(
                existing.getId(),
                existing.getCarId(),
                request.kwhCharged()             != null ? request.kwhCharged()             : existing.getKwhCharged(),
                request.costEur()                != null ? request.costEur()                : existing.getCostEur(),
                request.chargeDurationMinutes()  != null ? request.chargeDurationMinutes()  : existing.getChargeDurationMinutes(),
                geohash,
                request.odometerKm()             != null ? request.odometerKm()             : existing.getOdometerKm(),
                request.maxChargingPowerKw()     != null ? request.maxChargingPowerKw()     : existing.getMaxChargingPowerKw(),
                request.socAfterChargePercent()  != null ? request.socAfterChargePercent()  : existing.getSocAfterChargePercent(),
                request.socBeforeChargePercent() != null ? request.socBeforeChargePercent() : existing.getSocBeforeChargePercent(),
                request.loggedAt()               != null ? request.loggedAt()               : existing.getLoggedAt(),
                existing.getDataSource(),
                existing.isIncludeInStatistics(),
                existing.getOdometerSuggestionMinKm(),
                existing.getOdometerSuggestionMaxKm(),
                existing.getTemperatureCelsius(),
                request.chargingType() != null ? request.chargingType() : existing.getChargingType(),
                existing.getRawImportData(),
                existing.getCreatedAt(),
                LocalDateTime.now(),
                request.routeType() != null ? request.routeType() : existing.getRouteType(),
                request.tireType() != null ? request.tireType() : existing.getTireType(),
                existing.getSupersededBy()
        );

        EvLog savedLog = evLogRepository.save(updated);

        // Async: re-enrich temperature if location was added/changed
        if (geohashChanged || (savedLog.getGeohash() != null && savedLog.getTemperatureCelsius() == null)) {
            temperatureEnrichmentService.enrichLog(savedLog.getId(), savedLog.getGeohash(), savedLog.getLoggedAt());
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

    public List<EvLogResponse> getLogsForCar(UUID carId, UUID userId, Integer limit, int page) {
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new IllegalArgumentException("Car not found"));

        if (!car.getUserId().equals(userId)) {
            throw new IllegalArgumentException("User does not own the specified car");
        }

        // All logs sorted ascending — needed for consumption context (logX lookups)
        List<EvLog> allLogsSorted = evLogRepository.findAllByCarId(carId).stream()
                .sorted(Comparator.comparing(EvLog::getLoggedAt))
                .collect(Collectors.toList());

        // Compute per-log consumption + plausibility on the full dataset (SoC-based)
        Map<UUID, ConsumptionResult> consumptionByLog = new LinkedHashMap<>(car.getBatteryCapacityKwh() != null
                ? calculateConsumptionPerLog(allLogsSorted, car.getBatteryCapacityKwh(), lookupWltp(car))
                : Map.of());

        // Distance since last charge — covers logs with odometer regardless of SoC availability
        Map<UUID, Integer> distanceByLogId = computeDistanceByLogId(allLogsSorted);

        // Fallback: for logs with distance but no SoC-based consumption, estimate via kWh_charged/distance.
        // Marked as estimated=true so the frontend can display it differently (e.g. "~16.35 kWh/100km").
        // Less accurate than SoC-based (kWh_charged ≠ kWh_consumed), but useful when no SoC is available.
        for (EvLog log : allLogsSorted) {
            if (consumptionByLog.containsKey(log.getId())) continue;
            Integer dist = distanceByLogId.get(log.getId());
            if (dist == null || dist < plausibility.getMinTripDistanceKm()) continue;
            double c = log.getKwhCharged().doubleValue() / dist * 100.0;
            if (c < plausibility.getAbsoluteMinKwhPer100km() || c > plausibility.getAbsoluteMaxKwhPer100km()) continue;
            consumptionByLog.put(log.getId(), new ConsumptionResult(
                    BigDecimal.valueOf(c).setScale(2, RoundingMode.HALF_UP), true, dist, true));
        }

        // Return the requested page, enriched with consumption and distance data
        List<EvLog> page_logs = (limit != null && limit > 0)
                ? evLogRepository.findLatestByCarId(carId, limit, page)
                : allLogsSorted.reversed();

        return page_logs.stream()
                .map(log -> EvLogResponse.fromDomain(log, consumptionByLog.get(log.getId()), distanceByLogId.get(log.getId())))
                .toList();
    }

    /**
     * Get statistics for a specific car.
     * Includes key metrics for charging events and charge over time data.
     *
     * @param carId The car ID
     * @param userId The user ID (for ownership verification)
     * @param startDate Optional filter: start date (inclusive)
     * @param endDate Optional filter: end date (inclusive)
     * @param groupBy Aggregation level: DAY, WEEK, or MONTH
     */
    public EvLogStatisticsResponse getStatistics(UUID carId, UUID userId,
            java.time.LocalDate startDate, java.time.LocalDate endDate, String groupBy) {
        // Verify ownership
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new IllegalArgumentException("Car not found"));

        if (!car.getUserId().equals(userId)) {
            throw new IllegalArgumentException("User does not own the specified car");
        }

        // Check if user is a seed user (for demo mode)
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        boolean isSeedUser = user.isSeedData();

        // Load all logs once, sorted — needed as full context for consumption calculations
        List<EvLog> allLogsForCar = evLogRepository.findAllByCarId(carId).stream()
                .sorted(Comparator.comparing(EvLog::getLoggedAt))
                .collect(Collectors.toList());

        // Derive stats-filtered + time-filtered view without a second DB call
        // Demo Mode: Seed users see ALL their logs (including their own seed data)
        List<EvLog> logs = allLogsForCar.stream()
                .filter(log -> isSeedUser || log.isIncludeInStatistics())
                .filter(log -> log.isLoggedWithin(startDate, endDate))
                .collect(Collectors.toList());

        if (logs.isEmpty()) {
            return createEmptyStatistics();
        }

        // Compute per-log consumption once — used for both chart data and overall average
        Map<UUID, ConsumptionResult> consumptionByLog = new LinkedHashMap<>(car.getBatteryCapacityKwh() != null
                ? calculateConsumptionPerLog(allLogsForCar, car.getBatteryCapacityKwh(), lookupWltp(car))
                : Map.of());

        // Fallback: for logs with distance but no SoC-based consumption, estimate via kWh/distance.
        // Must run before groupChargesByPeriod() so chart data is populated even without SoC.
        Map<UUID, Integer> distanceByLogId = computeDistanceByLogId(allLogsForCar);
        for (EvLog log : allLogsForCar) {
            if (consumptionByLog.containsKey(log.getId())) continue;
            if (log.getKwhCharged() == null) continue;
            Integer dist = distanceByLogId.get(log.getId());
            if (dist == null || dist < plausibility.getMinTripDistanceKm()) continue;
            double c = log.getKwhCharged().doubleValue() / dist * 100.0;
            if (c < plausibility.getAbsoluteMinKwhPer100km() || c > plausibility.getAbsoluteMaxKwhPer100km()) continue;
            consumptionByLog.put(log.getId(), new ConsumptionResult(
                    BigDecimal.valueOf(c).setScale(2, RoundingMode.HALF_UP), true, dist, true));
        }

        // Calculate key metrics
        BigDecimal totalKwhCharged = logs.stream()
                .map(EvLog::getKwhCharged)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCostEur = logs.stream()
                .map(EvLog::getCostEur)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal avgCostPerKwh = totalKwhCharged.compareTo(BigDecimal.ZERO) > 0
                ? totalCostEur.divide(totalKwhCharged, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal cheapestCharge = logs.stream()
                .map(EvLog::getCostEur)
                .filter(Objects::nonNull)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        BigDecimal mostExpensiveCharge = logs.stream()
                .map(EvLog::getCostEur)
                .filter(Objects::nonNull)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        Integer avgChargeDuration = (int) logs.stream()
                .map(EvLog::getChargeDurationMinutes)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0.0);

        // Charge over time data (grouped and sorted by time)
        List<EvLogStatisticsResponse.ChargeDataPoint> chargesOverTime =
                groupChargesByPeriod(logs, groupBy != null ? groupBy : "MONTH", consumptionByLog);

        // Total distance and avg consumption — SoC-based from pre-computed consumptionByLog
        BigDecimal totalWeighted = BigDecimal.ZERO;
        int totalDist = 0;        // only plausible, for avg consumption calculation
        int totalDistAll = 0;     // all logs with distance, for display
        int estimatedCount = 0;   // count logs with estimated consumption (kWh/distance fallback)
        for (EvLog log : logs) {
            ConsumptionResult cr = consumptionByLog.get(log.getId());
            if (cr == null) continue;
            if (cr.distanceKm() > 0) totalDistAll += cr.distanceKm();
            if (!cr.plausible()) continue;
            if (cr.estimated()) estimatedCount++;
            totalWeighted = totalWeighted.add(cr.value().multiply(BigDecimal.valueOf(cr.distanceKm())));
            totalDist += cr.distanceKm();
        }
        BigDecimal totalDistanceKm = totalDistAll > 0 ? BigDecimal.valueOf(totalDistAll) : null;
        BigDecimal avgConsumptionKwhPer100km = weightedAverage(totalWeighted, totalDist);

        // Fallback: if no SoC data available, use kWh/distance (distanceByLogId already computed above)
        if (avgConsumptionKwhPer100km == null) {
            List<EvLog> logsWithDistance = logs.stream()
                    .filter(l -> distanceByLogId.containsKey(l.getId()) && distanceByLogId.get(l.getId()) > 0)
                    .collect(Collectors.toList());
            if (!logsWithDistance.isEmpty()) {
                int totalDistInt = logsWithDistance.stream().mapToInt(l -> distanceByLogId.get(l.getId())).sum();
                totalDistanceKm = BigDecimal.valueOf(totalDistInt);
                avgConsumptionKwhPer100km = calculateConsumptionFallback(logsWithDistance, totalDistanceKm);
                estimatedCount = logsWithDistance.size(); // all are estimated in pure fallback mode
            }
        }

        // Seasonal consumption — uses ALL logs (not time-filtered) for best signal
        SeasonalConsumptionResult seasonal = calculateSeasonalConsumption(List.of(car), isSeedUser);

        return new EvLogStatisticsResponse(
                totalKwhCharged,
                totalCostEur,
                avgCostPerKwh,
                cheapestCharge,
                mostExpensiveCharge,
                avgChargeDuration,
                logs.size(),
                totalDistanceKm,
                avgConsumptionKwhPer100km,
                estimatedCount,
                seasonal.summerConsumptionKwhPer100km(),
                seasonal.winterConsumptionKwhPer100km(),
                chargesOverTime
        );
    }

    private EvLogStatisticsResponse createEmptyStatistics() {
        return new EvLogStatisticsResponse(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, 0, 0,
                null, null, 0, null, null, List.of()
        );
    }

    /**
     * Calculates consumption (kWh/100km) for the trip from logX to logY.
     *
     * logX (trip start): logX.canBeUsedAsLogX() must be true
     *                    requires: odometer + socAfterChargePercent
     * logY (trip end):   logY.isComplete() must be true
     *                    requires: odometer + kwhCharged + socAfterChargePercent
     *
     * Formula:
     *   socBefore(logY)  = socAfter(logY) - kwhCharged(logY) / batteryCapacity * 100
     *   energyConsumed   = (socAfter(logX) - socBefore(logY)) * batteryCapacity / 100
     *   consumption      = energyConsumed / distance * 100
     *
     * Package-private for unit testing.
     *
     * @return consumption in kWh/100km, or empty if data is insufficient or result is invalid
     */
    Optional<BigDecimal> calculateConsumption(EvLog logX, EvLog logY, BigDecimal batteryCapacityKwh) {
        if (!logX.canBeUsedAsLogX() || !logY.isComplete()) return Optional.empty();
        if (batteryCapacityKwh == null || batteryCapacityKwh.compareTo(BigDecimal.ZERO) <= 0) return Optional.empty();

        int distance = logY.getOdometerKm() - logX.getOdometerKm();
        if (distance <= 0) return Optional.empty();

        // socBefore(logY) = socAfter(logY) - kwhCharged(logY) / batteryCapacity * 100
        BigDecimal socBeforeLogYPercent = BigDecimal.valueOf(logY.getSocAfterChargePercent())
                .subtract(logY.getKwhCharged()
                        .divide(batteryCapacityKwh, 4, RoundingMode.HALF_UP)
                        .multiply(HUNDRED));

        // energyConsumed = (socAfter(logX) - socBefore(logY)) * batteryCapacity / 100
        BigDecimal energyConsumedKwh = BigDecimal.valueOf(logX.getSocAfterChargePercent())
                .subtract(socBeforeLogYPercent)
                .multiply(batteryCapacityKwh)
                .divide(HUNDRED, 4, RoundingMode.HALF_UP);

        if (energyConsumedKwh.compareTo(BigDecimal.ZERO) <= 0) return Optional.empty();

        return Optional.of(energyConsumedKwh
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(distance), 2, RoundingMode.HALF_UP));
    }

    /**
     * Calculates per-log consumption (kWh/100km) for each complete log, with a plausibility verdict.
     *
     * Two-pass algorithm:
     *   Pass 1 — compute raw consumption for every complete log (isComplete + previous log with odometer).
     *            Trips shorter than minTripDistanceKm are excluded (unreliable odometer data).
     *   Pass 2 — check each value against the full distribution + WLTP reference via isConsumptionPlausible().
     *
     * @return map of logId (logY) → ConsumptionResult(value, plausible, distanceKm)
     */
    Map<UUID, ConsumptionResult> calculateConsumptionPerLog(List<EvLog> allLogs, BigDecimal batteryCapacityKwh, BigDecimal wltpKwh) {
        // Always sort — correctness must not depend on caller discipline
        List<EvLog> sorted = allLogs.stream()
                .sorted(Comparator.comparing(EvLog::getLoggedAt))
                .collect(Collectors.toList());

        // Pass 1: raw consumptions
        List<UUID> ids = new ArrayList<>();
        List<BigDecimal> values = new ArrayList<>();
        List<Integer> distances = new ArrayList<>();

        sorted.stream()
                .filter(EvLog::isComplete)
                .forEach(logY -> {
                    EvLog logX = findPreviousLog(sorted, logY);
                    if (logX == null) return;
                    int dist = logY.getOdometerKm() - logX.getOdometerKm();
                    if (dist < plausibility.getMinTripDistanceKm()) return;
                    calculateConsumption(logX, logY, batteryCapacityKwh).ifPresent(c -> {
                        ids.add(logY.getId());
                        values.add(c);
                        distances.add(dist);
                    });
                });

        // Pass 2: plausibility check — absolute bounds + statistical/WLTP reference
        Map<UUID, ConsumptionResult> result = new LinkedHashMap<>();
        for (int i = 0; i < ids.size(); i++) {
            boolean plausible = isConsumptionPlausible(values.get(i), values, wltpKwh);
            result.put(ids.get(i), new ConsumptionResult(values.get(i), plausible, distances.get(i)));
        }
        return result;
    }

    /**
     * Checks whether a calculated consumption value is plausible for this car.
     *
     * Three layers:
     *   Layer 1 — Absolute sanity bounds: [absoluteMin, absoluteMax] kWh/100km (always applied).
     *   Layer 2a — Statistical check (≥ minTripsForStatistical trips):
     *              mean(history) ± sigmaMultiplier × stdDev(history).
     *   Layer 2b — WLTP bootstrap (< minTripsForStatistical, WLTP available):
     *              [WLTP × wltpLowerFactor, WLTP × wltpUpperFactor].
     *   Layer 2c — Only absolute bounds apply (no history, no WLTP).
     *
     * Implausible values likely indicate a missing charging session between logX and logY.
     *
     * @param consumptionKwhPer100km     the calculated value to check
     * @param historicalConsumptions     all computed consumptions for this car (may include self)
     * @param wltpConsumptionKwhPer100km the car's WLTP reference value (nullable)
     */
    boolean isConsumptionPlausible(BigDecimal consumptionKwhPer100km,
                                   List<BigDecimal> historicalConsumptions,
                                   BigDecimal wltpConsumptionKwhPer100km) {
        // Layer 1: absolute sanity bounds
        BigDecimal absMin = BigDecimal.valueOf(plausibility.getAbsoluteMinKwhPer100km());
        BigDecimal absMax = BigDecimal.valueOf(plausibility.getAbsoluteMaxKwhPer100km());
        if (consumptionKwhPer100km.compareTo(absMin) < 0) return false;
        if (consumptionKwhPer100km.compareTo(absMax) > 0) return false;

        List<BigDecimal> history = historicalConsumptions != null ? historicalConsumptions : List.of();

        // Layer 2a: statistical check
        if (history.size() >= plausibility.getMinTripsForStatistical()) {
            BigDecimal mean = computeMean(history);
            BigDecimal stdDev = computeStdDev(history, mean);
            if (stdDev.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal margin = stdDev.multiply(BigDecimal.valueOf(plausibility.getSigmaMultiplier()));
                return consumptionKwhPer100km.compareTo(mean.subtract(margin)) >= 0
                        && consumptionKwhPer100km.compareTo(mean.add(margin)) <= 0;
            }
            // stdDev == 0: all values identical — accept only if within 10% of mean
            BigDecimal tolerance = mean.multiply(new BigDecimal("0.10"));
            return consumptionKwhPer100km.subtract(mean).abs().compareTo(tolerance) <= 0;
        }

        // Layer 2b: WLTP bootstrap
        if (wltpConsumptionKwhPer100km != null && wltpConsumptionKwhPer100km.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal lower = wltpConsumptionKwhPer100km.multiply(BigDecimal.valueOf(plausibility.getWltpLowerFactor()));
            BigDecimal upper = wltpConsumptionKwhPer100km.multiply(BigDecimal.valueOf(plausibility.getWltpUpperFactor()));
            return consumptionKwhPer100km.compareTo(lower) >= 0
                    && consumptionKwhPer100km.compareTo(upper) <= 0;
        }

        // Layer 2c: only absolute bounds (already passed above)
        return true;
    }

    private BigDecimal computeMean(List<BigDecimal> values) {
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(new BigDecimal(values.size()), 6, RoundingMode.HALF_UP);
    }

    private BigDecimal computeStdDev(List<BigDecimal> values, BigDecimal mean) {
        BigDecimal sumSquares = values.stream()
                .map(v -> v.subtract(mean).pow(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal variance = sumSquares.divide(new BigDecimal(values.size()), 10, RoundingMode.HALF_UP);
        return variance.sqrt(new MathContext(10, RoundingMode.HALF_UP));
    }

    /**
     * Fallback consumption calculation when SoC data is missing.
     * Simple formula: (total kWh charged / total distance) × 100
     * Less accurate for partial charging but works when SoC is unavailable.
     * Package-private for testing.
     */
    BigDecimal calculateConsumptionFallback(List<EvLog> logs, BigDecimal totalDistanceKm) {
        if (totalDistanceKm == null || totalDistanceKm.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        BigDecimal totalKwh = logs.stream()
                .map(EvLog::getKwhCharged)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return totalKwh
                .multiply(HUNDRED)
                .divide(totalDistanceKm, 2, RoundingMode.HALF_UP);
    }

    /**
     * Looks up the WLTP combined consumption for the given car via VehicleSpecification.
     * Returns null if no spec exists (e.g. model not yet in the database).
     */
    BigDecimal lookupWltp(Car car) {
        if (car.getModel() == null || car.getBatteryCapacityKwh() == null) return null;
        return vehicleSpecificationRepository
                .findByCarBrandAndModelAndCapacityAndType(
                        car.getModel().getBrand().name(),
                        car.getModel().name(),
                        car.getBatteryCapacityKwh(),
                        VehicleSpecification.WltpType.COMBINED)
                .map(VehicleSpecification::getWltpConsumptionKwhPer100km)
                .orElse(null);
    }

    /**
     * Returns the immediately preceding log (by loggedAt) as a candidate for logX.
     * Returns null if the directly previous log does not satisfy canBeUsedAsLogX().
     *
     * Strict-by-design: any log between logX and logY — even an incomplete one —
     * represents an unknown energy event. Skipping over it would silently corrupt
     * the SoC-delta calculation. If the direct predecessor isn't usable, the trip
     * cannot be calculated at all.
     *
     * Callers must pass a list sorted ascending by loggedAt.
     * calculateConsumptionPerLog() guarantees this internally.
     */
    private EvLog findPreviousLog(List<EvLog> sortedLogs, EvLog logY) {
        for (int i = 0; i < sortedLogs.size(); i++) {
            if (sortedLogs.get(i).getId().equals(logY.getId())) {
                if (i == 0) return null;
                EvLog candidate = sortedLogs.get(i - 1);
                return candidate.canBeUsedAsLogX() ? candidate : null;
            }
        }
        return null;
    }

    /**
     * Compute distance driven before each charge from consecutive odometer readings.
     * Distance for log[i] = log[i].odometer - log[j].odometer where j is the most recent log with odometer data.
     * Skips logs without odometer data to find the actual previous trip.
     * Logs must be sorted by loggedAt ascending.
     */
    private Map<UUID, Integer> computeDistanceByLogId(List<EvLog> sortedLogs) {
        Map<UUID, Integer> result = new java.util.HashMap<>();
        for (int i = 1; i < sortedLogs.size(); i++) {
            EvLog current = sortedLogs.get(i);
            if (current.getOdometerKm() == null) {
                continue; // Skip logs without odometer
            }

            // Find previous log with odometer data (search backwards)
            EvLog previous = null;
            for (int j = i - 1; j >= 0; j--) {
                if (sortedLogs.get(j).getOdometerKm() != null) {
                    previous = sortedLogs.get(j);
                    break;
                }
            }

            if (previous != null) {
                int distance = current.getOdometerKm() - previous.getOdometerKm();
                if (distance > 0) {
                    result.put(current.getId(), distance);
                }
            }
        }
        return result;
    }

    /** Distance-weighted average, null if no data. */
    private BigDecimal weightedAverage(BigDecimal totalWeighted, int totalKm) {
        if (totalKm == 0) return null;
        return totalWeighted.divide(BigDecimal.valueOf(totalKm), 2, RoundingMode.HALF_UP);
    }

    private record PlausibleEntry(EvLog log, BigDecimal consumptionKwhPer100km, int distanceKm, boolean estimated) {}

    /**
     * Returns all plausible consumption entries for a car: SoC-based first, fallback if no SoC data.
     * Encapsulates the two-path logic shared by community avg and seasonal calculations.
     */
    private List<PlausibleEntry> getPlausibleEntriesForCar(Car car, List<EvLog> allLogs, List<EvLog> statsLogs) {
        Map<UUID, ConsumptionResult> perLog = calculateConsumptionPerLog(
                allLogs, car.getBatteryCapacityKwh(), lookupWltp(car));

        List<PlausibleEntry> entries = new ArrayList<>();

        // Pass 1: SoC-based consumption (plausible only)
        for (EvLog log : statsLogs) {
            ConsumptionResult cr = perLog.get(log.getId());
            if (cr == null || !cr.plausible()) continue;
            entries.add(new PlausibleEntry(log, cr.value(), cr.distanceKm(), false));
        }

        // Pass 2: Fallback kWh/distance for logs without SoC result (hybrid approach)
        // This allows using old Sprit-Monitor imports without SoC data alongside newer logs with SoC.
        // Marked as estimated (less accurate, but better than nothing).
        Map<UUID, Integer> distanceByLogId = computeDistanceByLogId(allLogs);
        for (EvLog log : statsLogs) {
            // Skip if already computed via SoC (plausible)
            if (perLog.containsKey(log.getId()) && perLog.get(log.getId()).plausible()) continue;

            Integer dist = distanceByLogId.get(log.getId());
            if (dist == null || dist < plausibility.getMinTripDistanceKm()) continue;

            double c = log.getKwhCharged().doubleValue() / dist * 100;
            if (c < plausibility.getAbsoluteMinKwhPer100km() || c > plausibility.getAbsoluteMaxKwhPer100km()) continue;

            entries.add(new PlausibleEntry(log, BigDecimal.valueOf(c).setScale(2, RoundingMode.HALF_UP), dist, true));
        }
        return entries;
    }

    /**
     * Calculates the community average consumption (kWh/100km) for a list of cars.
     *
     * Uses the same logic as per-user statistics (SoC-based → fallback), applied per car.
     * Results are distance-weighted across all cars to give a fair community average.
     *
     * isSeedUser=true: includes all logs (for demo mode).
     * isSeedUser=false: only logs with includeInStatistics=true.
     *
     * Trips with implausible consumption (outside absoluteMin/Max) are excluded
     * in the fallback path via per-trip filtering with PlausibilityProperties.
     *
     * @return distance-weighted avg kWh/100km, or null if no valid data
     */
    public CommunityConsumptionResult calculateCommunityAvgConsumption(List<Car> cars, boolean isSeedUser) {
        if (cars.isEmpty()) return CommunityConsumptionResult.EMPTY;

        List<UUID> carIds = cars.stream().map(Car::getId).toList();
        Map<UUID, List<EvLog>> logsByCarId = evLogRepository.findAllByCarIds(carIds).stream()
                .sorted(Comparator.comparing(EvLog::getLoggedAt))
                .collect(Collectors.groupingBy(EvLog::getCarId));

        BigDecimal totalWeighted = BigDecimal.ZERO;
        int totalDistance = 0;
        int tripCount = 0;
        int estimatedTripCount = 0;

        for (Car car : cars) {
            List<EvLog> allLogs = logsByCarId.getOrDefault(car.getId(), List.of());
            List<EvLog> statsLogs = allLogs.stream()
                    .filter(l -> isSeedUser || l.isIncludeInStatistics())
                    .toList();
            if (statsLogs.isEmpty()) continue;

            List<PlausibleEntry> entries = getPlausibleEntriesForCar(car, allLogs, statsLogs);
            for (PlausibleEntry e : entries) {
                totalWeighted = totalWeighted.add(e.consumptionKwhPer100km().multiply(BigDecimal.valueOf(e.distanceKm())));
                totalDistance += e.distanceKm();
                if (e.estimated()) estimatedTripCount++;
            }
            tripCount += entries.size();
        }

        return new CommunityConsumptionResult(weightedAverage(totalWeighted, totalDistance), tripCount, estimatedTripCount);
    }

    /**
     * Result type for calculateSeasonalConsumption().
     * Distances in km, consumptions in kWh/100km (null if no data for that season).
     */
    public record SeasonalConsumptionResult(
            BigDecimal summerConsumptionKwhPer100km,
            BigDecimal winterConsumptionKwhPer100km,
            BigDecimal totalConsumptionKwhPer100km,  // distance-weighted total, consistent with seasonal values
            int summerKm,
            int winterKm,
            int summerLogCount,
            int winterLogCount
    ) {}

    /**
     * Calculates seasonal (summer/winter) community consumption for a list of cars.
     * Summer: April–September (months 4–9), Winter: October–March (months 1–3, 10–12).
     *
     * Uses the same SoC-based logic as calculateCommunityAvgConsumption(), bucketed by season.
     * Fallback (kWh/distance) is applied per-car when no SoC data is available.
     *
     * @return SeasonalConsumptionResult with nullable consumptions if no data for a season
     */
    public SeasonalConsumptionResult calculateSeasonalConsumption(List<Car> cars, boolean isSeedUser) {
        if (cars.isEmpty()) return new SeasonalConsumptionResult(null, null, null, 0, 0, 0, 0);

        List<UUID> carIds = cars.stream().map(Car::getId).toList();
        Map<UUID, List<EvLog>> logsByCarId = evLogRepository.findAllByCarIds(carIds).stream()
                .sorted(Comparator.comparing(EvLog::getLoggedAt))
                .collect(Collectors.groupingBy(EvLog::getCarId));

        BigDecimal summerWeighted = BigDecimal.ZERO;
        BigDecimal winterWeighted = BigDecimal.ZERO;
        BigDecimal totalWeighted = BigDecimal.ZERO;
        int summerKm = 0, winterKm = 0, totalKm = 0, summerLogCount = 0, winterLogCount = 0;

        for (Car car : cars) {
            List<EvLog> allLogs = logsByCarId.getOrDefault(car.getId(), List.of());
            List<EvLog> statsLogs = allLogs.stream()
                    .filter(l -> isSeedUser || l.isIncludeInStatistics())
                    .toList();
            if (statsLogs.isEmpty()) continue;

            for (PlausibleEntry e : getPlausibleEntriesForCar(car, allLogs, statsLogs)) {
                BigDecimal weighted = e.consumptionKwhPer100km().multiply(BigDecimal.valueOf(e.distanceKm()));
                int month = e.log().getLoggedAt().getMonthValue();
                totalWeighted = totalWeighted.add(weighted);
                totalKm += e.distanceKm();
                if (month >= 5 && month <= 8) {
                    summerWeighted = summerWeighted.add(weighted);
                    summerKm += e.distanceKm();
                    summerLogCount++;
                } else if (month == 11 || month == 12 || month == 1 || month == 2) {
                    winterWeighted = winterWeighted.add(weighted);
                    winterKm += e.distanceKm();
                    winterLogCount++;
                }
            }
        }

        return new SeasonalConsumptionResult(
                weightedAverage(summerWeighted, summerKm),
                weightedAverage(winterWeighted, winterKm),
                weightedAverage(totalWeighted, totalKm),
                summerKm, winterKm, summerLogCount, winterLogCount);
    }

    /**
     * Group charges by time period (DAY, WEEK, MONTH) and aggregate metrics.
     * Uses pre-computed SoC-based ConsumptionResult values for distance and consumption per period,
     * consistent with the overall statistics calculation.
     */
    private List<EvLogStatisticsResponse.ChargeDataPoint> groupChargesByPeriod(
            List<EvLog> logs, String groupBy, Map<UUID, ConsumptionResult> consumptionByLog) {

        Map<String, List<EvLog>> groupedLogs = new java.util.LinkedHashMap<>();
        for (EvLog log : logs) {
            String periodKey = getPeriodKey(log.getLoggedAt(), groupBy);
            groupedLogs.computeIfAbsent(periodKey, k -> new ArrayList<>()).add(log);
        }

        return groupedLogs.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    List<EvLog> periodLogs = entry.getValue();

                    BigDecimal totalKwh = periodLogs.stream()
                            .map(EvLog::getKwhCharged)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    BigDecimal totalCost = periodLogs.stream()
                            .map(EvLog::getCostEur)
                            .filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    // Distance and consumption: SoC-based, plausible logs only
                    BigDecimal periodWeighted = BigDecimal.ZERO;
                    int periodDist = 0;
                    for (EvLog log : periodLogs) {
                        ConsumptionResult cr = consumptionByLog.get(log.getId());
                        if (cr == null) continue;
                        periodWeighted = periodWeighted.add(cr.value().multiply(BigDecimal.valueOf(cr.distanceKm())));
                        periodDist += cr.distanceKm();
                    }

                    BigDecimal periodDistance = periodDist > 0 ? BigDecimal.valueOf(periodDist) : null;
                    BigDecimal periodConsumption = weightedAverage(periodWeighted, periodDist);

                    LocalDateTime periodTimestamp = periodLogs.get(0).getLoggedAt();
                    return new EvLogStatisticsResponse.ChargeDataPoint(
                            periodTimestamp, totalCost, totalKwh, periodDistance, periodConsumption
                    );
                })
                .collect(Collectors.toList());
    }

    /**
     * Generate a period key for grouping (e.g., "2025-02", "2025-W08", "2025-02-15").
     */
    private String getPeriodKey(LocalDateTime timestamp, String groupBy) {
        java.time.LocalDate date = timestamp.toLocalDate();

        return switch (groupBy.toUpperCase()) {
            case "DAY" -> date.toString(); // "2025-02-15"
            case "WEEK" -> {
                // ISO week number
                int year = date.getYear();
                int week = date.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR);
                yield String.format("%d-W%02d", year, week);
            }
            case "MONTH" -> String.format("%d-%02d", date.getYear(), date.getMonthValue());
            default -> String.format("%d-%02d", date.getYear(), date.getMonthValue());
        };
    }
}
