package com.evmonitor.application.spritmonitor;

import ch.hsr.geohash.GeoHash;
import com.evmonitor.application.CoinLogService;
import com.evmonitor.domain.CoinType;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.EvLogRepository;
import com.evmonitor.infrastructure.external.SpritMonitorClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class SpritMonitorImportService {

    public static final DateTimeFormatter DD_MM_YYYY = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final String DATA_SOURCE = "SPRITMONITOR_IMPORT";
    private static final int IMPORT_COINS = 50;

    private final SpritMonitorClient client;
    private final EvLogRepository evLogRepository;
    private final CoinLogService coinLogService;

    public SpritMonitorImportService(SpritMonitorClient client, EvLogRepository evLogRepository,
                                     CoinLogService coinLogService) {
        this.client = client;
        this.evLogRepository = evLogRepository;
        this.coinLogService = coinLogService;
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
        UUID evMonitorCarId
    ) {
        ImportResult result = new ImportResult();

        try {
            List<SpritMonitorFuelingDTO> fuelings = client.getFuelings(token, spritMonitorVehicleId);

            for (SpritMonitorFuelingDTO fueling : fuelings) {
                try {
                    LocalDateTime loggedAt = LocalDate.parse(fueling.date(), DD_MM_YYYY).atStartOfDay();

                    // Skip if already imported (same car + date + source)
                    if (evLogRepository.existsByCarIdAndLoggedAtAndDataSource(evMonitorCarId, loggedAt, DATA_SOURCE)) {
                        result.incrementSkipped();
                        continue;
                    }

                    EvLog evLog = convertToEvLog(fueling, evMonitorCarId, loggedAt);
                    evLogRepository.save(evLog);
                    result.incrementImported();
                } catch (Exception e) {
                    log.error("Failed to import fueling from " + fueling.date() + ": " + e.getMessage(), e);
                    result.addError("Failed to import fueling from " + fueling.date() + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            result.addError("Failed to fetch fuelings: " + e.getMessage());
        }

        // Award one-time 50 Watt for first-ever Sprit-Monitor import
        if (result.getImported() > 0
                && !coinLogService.hasEverReceivedCoinForAction(userId, CoinLogService.ACTION_SPRITMONITOR_IMPORTED)) {
            coinLogService.awardCoins(userId, CoinType.ACHIEVEMENT_COIN, IMPORT_COINS,
                    CoinLogService.ACTION_SPRITMONITOR_IMPORTED);
            result.addCoinsAwarded(IMPORT_COINS);
        }

        return result;
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

        return EvLog.createNewWithSource(
            carId,
            kwhCharged,
            costEur,
            durationMinutes,
            geohash,
            null,
            null,
            loggedAt,
            DATA_SOURCE
        );
    }
}
