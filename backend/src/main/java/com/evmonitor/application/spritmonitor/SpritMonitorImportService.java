package com.evmonitor.application.spritmonitor;

import ch.hsr.geohash.GeoHash;
import com.evmonitor.application.CoinLogService;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.ChargingType;
import com.evmonitor.domain.DataSource;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.EvLogRepository;
import com.evmonitor.infrastructure.external.SpritMonitorClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class SpritMonitorImportService {

    public static final DateTimeFormatter DD_MM_YYYY = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DataSource DATA_SOURCE = DataSource.SPRITMONITOR_IMPORT;

    private final SpritMonitorClient client;
    private final EvLogRepository evLogRepository;
    private final CarRepository carRepository;
    private final CoinLogService coinLogService;
    private final ObjectMapper objectMapper;

    public SpritMonitorImportService(SpritMonitorClient client, EvLogRepository evLogRepository,
                                     CarRepository carRepository, CoinLogService coinLogService,
                                     ObjectMapper objectMapper) {
        this.client = client;
        this.evLogRepository = evLogRepository;
        this.carRepository = carRepository;
        this.coinLogService = coinLogService;
        this.objectMapper = objectMapper;
    }

    /**
     * Fetches electric vehicles from Sprit-Monitor
     *
     * @param token Sprit-Monitor API token (NOT persisted!)
     * @return List of electric vehicles
     */
    public List<SpritMonitorVehicleDTO> fetchVehicles(String token) {
        return client.getVehicles(token);
    }

    /**
     * Imports fuelings from Sprit-Monitor for a specific vehicle
     *
     * @param userId EV Monitor user ID (for ownership checks)
     * @param token Sprit-Monitor API token (NOT persisted!)
     * @param spritMonitorVehicleId Sprit-Monitor vehicle ID
     * @param evMonitorCarId EV Monitor car ID (must belong to userId)
     * @return Import result with statistics
     */
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
            List<SpritMonitorFuelingDTO> fuelings = client.getFuelings(token, spritMonitorVehicleId, tankId);

            // Sort by date ASC, then by odometer ASC (nulls last) so that same-day charges
            // are processed in chronological order. SpritMonitor returns them newest-first,
            // which would produce reversed timestamps without this sort.
            // Defensive: unparseable dates sort to the end so the per-fueling error handler catches them.
            List<SpritMonitorFuelingDTO> sortedFuelings = fuelings.stream()
                    .sorted(Comparator
                            .comparing((SpritMonitorFuelingDTO f) -> {
                                try {
                                    return LocalDate.parse(f.date(), DD_MM_YYYY);
                                } catch (Exception e) {
                                    return LocalDate.MAX;
                                }
                            })
                            .thenComparing(f -> f.odometer() != null ? f.odometer() : new BigDecimal(Long.MAX_VALUE)))
                    .toList();

            // Track how many kWh fuelings we've seen per date to make timestamps unique
            // (multiple charges on the same day → 00:00:00, 00:00:01, 00:00:02 …)
            Map<String, Integer> perDateCounter = new HashMap<>();

            for (SpritMonitorFuelingDTO fueling : sortedFuelings) {
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

                    EvLog evLog = convertToEvLog(fueling, evMonitorCarId, loggedAt);
                    if (evLog.getGeohash() == null && fueling.stationname() != null && !fueling.stationname().isBlank()) {
                        result.incrementWithoutLocation();
                    }
                    EvLog savedLog = evLogRepository.save(evLog);
                    savedLogs.add(savedLog);
                    result.incrementImported();

                    // Award 2 coins per imported log, linked to the log for deletion deduction
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
            // Return early to avoid coin check on aborted transaction
            return result;
        }

        // Award one-time bonus for first-ever Sprit-Monitor import (idempotency enforced by awardCoinsForEvent)
        if (result.getImported() > 0) {
            result.addCoinsAwarded(coinLogService.awardCoinsForEvent(userId, CoinLogService.CoinEvent.SPRITMONITOR_CONNECTED, null));
        }

        return result;
    }

    /**
     * Deletes all Sprit-Monitor imports for the authenticated user.
     * Only deletes entries with data_source = SPRITMONITOR_IMPORT.
     * Does NOT delete USER_LOGGED entries or other import sources.
     *
     * @param userId EV Monitor user ID (ownership enforced by repository query)
     */
    @Transactional
    public void deleteAllImports(UUID userId) {
        evLogRepository.deleteAllByUserIdAndDataSource(userId, DATA_SOURCE);
    }

    /**
     * Converts Sprit-Monitor fueling to EV Monitor EvLog
     */
    private EvLog convertToEvLog(SpritMonitorFuelingDTO fueling, UUID carId, LocalDateTime loggedAt) {

        // Convert lat/lon to geohash (privacy-first!)
        String geohash = null;
        if (fueling.position() != null && fueling.position().lat() != null && fueling.position().lon() != null) {
            geohash = GeoHash.withCharacterPrecision(
                fueling.position().lat().doubleValue(),
                fueling.position().lon().doubleValue(),
                5
            ).toBase32();
        }

        // Handle null values with defaults
        BigDecimal kwhCharged = fueling.quantity() != null ? fueling.quantity() : BigDecimal.ZERO;
        BigDecimal costEur = fueling.cost() != null ? fueling.cost() : BigDecimal.ZERO;
        Integer durationMinutes = fueling.chargingDuration() != null ? fueling.chargingDuration() : 0;
        Integer odometerKm = fueling.odometer() != null ? fueling.odometer().intValue() : null;
        Integer socAfterChargePercent = fueling.percent() != null ? fueling.percent().intValue() : null;

        ChargingType chargingType = fueling.parseChargingType();

        String rawImportData = null;
        try {
            rawImportData = objectMapper.writeValueAsString(fueling);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize SpritMonitor fueling to JSON for rawImportData: {}", e.getMessage());
        }

        return EvLog.createNewWithSource(
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
            rawImportData
        );
    }
}
