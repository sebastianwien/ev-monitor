package com.evmonitor.application.spritmonitor;

import ch.hsr.geohash.GeoHash;
import com.evmonitor.application.CoinLogService;
import com.evmonitor.application.ingest.ChargingEntry;
import com.evmonitor.application.ingest.IngestCommand;
import com.evmonitor.application.ingest.IngestDoor;
import com.evmonitor.application.ingest.IngestGateway;
import com.evmonitor.application.ingest.IngestResult;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.DataSource;
import com.evmonitor.domain.RouteType;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.EvLogRepository;
import com.evmonitor.domain.exception.ForbiddenException;
import com.evmonitor.domain.exception.NotFoundException;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final IngestGateway ingestGateway;

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
        if (!car.isOwnedBy(userId)) {
            throw new IllegalArgumentException("User does not own the specified car");
        }

        ImportResult result = new ImportResult();
        List<ChargingEntry> entries = new ArrayList<>();
        // Station genannt, aber ohne Position: zählt erst, wenn die Ladung auch angelegt wird
        Set<LocalDateTime> stationWithoutLocation = new HashSet<>();

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
            // (multiple charges on the same day → 00:00, 00:01, 00:02 …). Die Position am Tag ist
            // damit die Identität einer Ladung für die Dedup im Gateway.
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

                    ChargingEntry entry = toEntry(fueling, loggedAt, rawFueling.rawJson());
                    if (entry.geohash() == null && fueling.stationname() != null && !fueling.stationname().isBlank()) {
                        stationWithoutLocation.add(loggedAt);
                    }
                    entries.add(entry);
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

        IngestResult ingested = ingest(userId, evMonitorCarId, entries);
        for (EvLog saved : ingested.created()) {
            result.incrementImported();
            result.addCoinsAwarded(CoinLogService.CoinEvent.SPRITMONITOR_LOG.getDefaultAmount());
            if (stationWithoutLocation.contains(saved.getLoggedAt())) {
                result.incrementWithoutLocation();
            }
        }
        for (int i = 0; i < ingested.skipped(); i++) {
            result.incrementSkipped();
        }
        if (ingested.errors() > 0) {
            result.addError(ingested.errors() + " fuelings could not be saved");
        }

        if (result.getImported() > 0) {
            result.addCoinsAwarded(coinLogService.awardCoinsForEvent(userId, CoinLogService.CoinEvent.SPRITMONITOR_CONNECTED, null));
        }

        return result;
    }

    /**
     * Anlegen, Dedup (minutengenau je Quelle, Tombstones zählen mit), Bepreisung und Watt je Ladung
     * übernimmt das Gateway. Die Ownership ist schon vor dem Abruf geprüft; verschwindet das Auto
     * dazwischen, bleibt es beim bisherigen Fehlerbild.
     */
    private IngestResult ingest(UUID userId, UUID carId, List<ChargingEntry> entries) {
        try {
            return ingestGateway.ingestCharging(
                    new IngestCommand(userId, carId, DATA_SOURCE, IngestDoor.IMPORT_BATCH, entries));
        } catch (NotFoundException e) {
            throw new IllegalArgumentException("Car not found with ID: " + carId, e);
        } catch (ForbiddenException e) {
            throw new IllegalArgumentException("User does not own the specified car", e);
        }
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
        if (!car.isOwnedBy(userId)) {
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

    private static ChargingEntry toEntry(SpritMonitorFuelingDTO fueling, LocalDateTime loggedAt, String rawJson) {
        String geohash = null;
        if (fueling.position() != null && fueling.position().lat() != null && fueling.position().lon() != null) {
            geohash = GeoHash.withCharacterPrecision(
                fueling.position().lat().doubleValue(),
                fueling.position().lon().doubleValue(),
                6
            ).toBase32();
        }

        return ChargingEntry.builder()
                .loggedAt(loggedAt)
                .kwhCharged(fueling.quantity() != null ? fueling.quantity() : BigDecimal.ZERO)
                // Fehlender Preis bleibt leer (nicht 0): das Gateway bepreist dann über den Ort. Den Heimtarif
                // bekommt Spritmonitor nicht, weil öffentlich/daheim unbekannt bleibt.
                .costEur(fueling.cost())
                .chargeDurationMinutes(fueling.chargingDuration() != null ? fueling.chargingDuration() : 0)
                .geohash(geohash)
                .odometerKm(fueling.odometer() != null ? fueling.odometer().intValue() : null)
                .maxChargingPowerKw(fueling.chargingPower())
                .socAfter(fueling.percent())
                .chargingType(fueling.parseChargingType())
                .routeType(fueling.parseRouteType())
                .rawImportData(rawJson)
                .build();
    }
}
