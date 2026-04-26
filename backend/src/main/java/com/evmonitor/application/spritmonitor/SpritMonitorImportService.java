package com.evmonitor.application.spritmonitor;

import ch.hsr.geohash.GeoHash;
import com.evmonitor.application.CoinLogService;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.ChargingType;
import com.evmonitor.domain.DataSource;
import com.evmonitor.domain.RouteType;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.EvLogRepository;
import com.evmonitor.infrastructure.external.SpritMonitorClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class SpritMonitorImportService {

    public static final DateTimeFormatter DD_MM_YYYY = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DataSource DATA_SOURCE = DataSource.SPRITMONITOR_IMPORT;

    private final SpritMonitorClient client;
    private final EvLogRepository evLogRepository;
    private final CarRepository carRepository;
    private final CoinLogService coinLogService;

    public List<SpritMonitorVehicleDTO> fetchVehicles(String token) {
        return client.getVehicles(token);
    }

    @Transactional
    public ImportResult importFuelings(
        UUID userId,
        String token,
        Integer spritMonitorVehicleId,
        Integer spritMonitorMainTankId,
        UUID evMonitorCarId
    ) {
        Car car = carRepository.findById(evMonitorCarId)
                .orElseThrow(() -> new IllegalArgumentException("Car not found with ID: " + evMonitorCarId));
        if (!car.getUserId().equals(userId)) {
            throw new IllegalArgumentException("User does not own the specified car");
        }

        ImportResult result = new ImportResult();
        List<EvLog> savedLogs = new ArrayList<>();

        try {
            int tankId = spritMonitorMainTankId != null ? spritMonitorMainTankId : 1;
            List<RawFueling> fuelings = client.getFuelings(token, spritMonitorVehicleId, tankId);

            // Sort by date ASC, then by odometer ASC (nulls last) so that same-day charges
            // are processed in chronological order. SpritMonitor returns them newest-first,
            // which would produce reversed timestamps without this sort.
            // Defensive: unparseable dates sort to the end so the per-fueling error handler catches them.
            List<RawFueling> sortedFuelings = fuelings.stream()
                    .sorted(Comparator
                            .comparing((RawFueling r) -> {
                                try {
                                    return LocalDate.parse(r.dto().date(), DD_MM_YYYY);
                                } catch (Exception e) {
                                    return LocalDate.MAX;
                                }
                            })
                            .thenComparing(r -> r.dto().odometer() != null ? r.dto().odometer() : new BigDecimal(Long.MAX_VALUE)))
                    .toList();

            // Track how many kWh fuelings we've seen per date to make timestamps unique
            // (multiple charges on the same day → 00:00:00, 00:00:01, 00:00:02 …)
            Map<String, Integer> perDateCounter = new HashMap<>();

            for (RawFueling rawFueling : sortedFuelings) {
                SpritMonitorFuelingDTO fueling = rawFueling.dto();
                try {
                    // Skip entries not in kWh — could be liters, kg, etc. from non-EV tanks
                    if (!fueling.isKwh()) {
                        log.debug("Skipping fueling on {} — not in kWh (quantityunitid={})",
                                fueling.date(), fueling.quantityUnitId());
                        result.incrementSkipped();
                        continue;
                    }

                    // Assign a per-day index so multiple charges on the same day get unique timestamps
                    int dayIndex = perDateCounter.merge(fueling.date(), 1, Integer::sum) - 1;
                    LocalDateTime loggedAt = LocalDate.parse(fueling.date(), DD_MM_YYYY).atStartOfDay().plusMinutes(dayIndex);

                    // Skip if already imported (same car + timestamp + source)
                    if (evLogRepository.existsByCarIdAndLoggedAtAndDataSource(evMonitorCarId, loggedAt, DATA_SOURCE)) {
                        result.incrementSkipped();
                        continue;
                    }

                    EvLog evLog = convertToEvLog(fueling, evMonitorCarId, loggedAt, rawFueling.rawJson());
                    if (evLog.getGeohash() == null && fueling.stationname() != null && !fueling.stationname().isBlank()) {
                        result.incrementWithoutLocation();
                    }
                    EvLog savedLog = evLogRepository.save(evLog);
                    savedLogs.add(savedLog);
                    result.incrementImported();

                    coinLogService.awardCoinsForEvent(userId, CoinLogService.CoinEvent.SPRITMONITOR_LOG, savedLog.getId());
                    result.addCoinsAwarded(CoinLogService.CoinEvent.SPRITMONITOR_LOG.getDefaultAmount());
                } catch (Exception e) {
                    log.error("Failed to import fueling from " + fueling.date() + ": " + e.getMessage(), e);
                    result.addError("Failed to import fueling from " + fueling.date() + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch fuelings: " + e.getMessage(), e);
            result.addError("Failed to fetch fuelings: " + e.getMessage());
            return result;
        }

        if (result.getImported() > 0) {
            result.addCoinsAwarded(coinLogService.awardCoinsForEvent(userId, CoinLogService.CoinEvent.SPRITMONITOR_CONNECTED, null));
        }

        return result;
    }

    @Transactional
    public void deleteAllImports(UUID userId) {
        evLogRepository.deleteAllByUserIdAndDataSource(userId, DATA_SOURCE);
    }

    @Transactional
    public RefreshRawResult refreshRawImportData(
            UUID userId,
            String token,
            Integer spritMonitorVehicleId,
            Integer spritMonitorMainTankId,
            UUID evMonitorCarId
    ) {
        Car car = carRepository.findById(evMonitorCarId)
                .orElseThrow(() -> new IllegalArgumentException("Car not found with ID: " + evMonitorCarId));
        if (!car.getUserId().equals(userId)) {
            throw new IllegalArgumentException("User does not own the specified car");
        }

        RefreshRawResult.Builder result = RefreshRawResult.builder();

        try {
            int tankId = spritMonitorMainTankId != null ? spritMonitorMainTankId : 1;
            List<RawFueling> fuelings = client.getFuelings(token, spritMonitorVehicleId, tankId);

            for (RawFueling rawFueling : fuelings) {
                SpritMonitorFuelingDTO fueling = rawFueling.dto();
                try {
                    if (!fueling.isKwh()) {
                        result.incrementSkipped();
                        continue;
                    }

                    LocalDate date = LocalDate.parse(fueling.date(), DD_MM_YYYY);
                    BigDecimal kwhCharged = fueling.quantity() != null ? fueling.quantity() : BigDecimal.ZERO;

                    List<EvLog> matches = evLogRepository.findByCarIdAndDateAndKwhChargedAndDataSource(
                            evMonitorCarId, date, kwhCharged, DATA_SOURCE);

                    if (matches.size() != 1) {
                        log.debug("Skipping raw refresh for {} kWh on {} - {} matches (expected 1)",
                                kwhCharged, date, matches.size());
                        result.incrementSkipped();
                        continue;
                    }

                    EvLog existing = matches.get(0);
                    evLogRepository.updateRawImportData(existing.getId(), rawFueling.rawJson());
                    RouteType routeType = fueling.parseRouteType();
                    if (routeType != null && existing.getRouteType() == null) {
                        evLogRepository.updateRouteType(existing.getId(), routeType);
                    }
                    result.incrementRefreshed();
                } catch (Exception e) {
                    log.error("Failed to refresh raw data for fueling on {}: {}", fueling.date(), e.getMessage(), e);
                    result.addError("Failed to refresh raw data for " + fueling.date() + ": " + e.getMessage());
                    result.incrementSkipped();
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch fuelings for raw refresh: {}", e.getMessage(), e);
            result.addError("Failed to fetch fuelings: " + e.getMessage());
        }

        return result.build();
    }

    private EvLog convertToEvLog(SpritMonitorFuelingDTO fueling, UUID carId, LocalDateTime loggedAt, String rawJson) {
        String geohash = null;
        if (fueling.position() != null && fueling.position().lat() != null && fueling.position().lon() != null) {
            geohash = GeoHash.withCharacterPrecision(
                fueling.position().lat().doubleValue(),
                fueling.position().lon().doubleValue(),
                6
            ).toBase32();
        }

        BigDecimal kwhCharged = fueling.quantity() != null ? fueling.quantity() : BigDecimal.ZERO;
        BigDecimal costEur = fueling.cost() != null ? fueling.cost() : BigDecimal.ZERO;
        Integer durationMinutes = fueling.chargingDuration() != null ? fueling.chargingDuration() : 0;
        Integer odometerKm = fueling.odometer() != null ? fueling.odometer().intValue() : null;
        BigDecimal socAfterChargePercent = fueling.percent();

        ChargingType chargingType = fueling.parseChargingType();
        RouteType routeType = fueling.parseRouteType();

        EvLog base = EvLog.createNewWithSource(
            carId,
            kwhCharged,
            costEur,
            durationMinutes,
            geohash,
            odometerKm,
            fueling.chargingPower(),
            socAfterChargePercent,
            loggedAt,
            DATA_SOURCE,
            chargingType,
            rawJson
        );
        return routeType != null ? base.toBuilder().routeType(routeType).build() : base;
    }
}
