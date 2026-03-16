package com.evmonitor.application.publicapi;

import ch.hsr.geohash.GeoHash;
import com.evmonitor.application.CoinLogService;
import com.evmonitor.application.SessionGroupService;
import com.evmonitor.domain.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class PublicApiImportService {

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ISO_OFFSET_DATE_TIME,
            DateTimeFormatter.ISO_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd")
    );

    private static final Pattern LAT_LON_PATTERN = Pattern.compile(
            "^(-?\\d{1,3}\\.\\d+)[,;\\s]+\\s*(-?\\d{1,3}\\.\\d+)$"
    );

    private final EvLogRepository evLogRepository;
    private final CarRepository carRepository;
    private final CoinLogService coinLogService;
    private final SessionGroupService sessionGroupService;

    public PublicApiImportService(EvLogRepository evLogRepository, CarRepository carRepository,
                                  CoinLogService coinLogService, SessionGroupService sessionGroupService) {
        this.evLogRepository = evLogRepository;
        this.carRepository = carRepository;
        this.coinLogService = coinLogService;
        this.sessionGroupService = sessionGroupService;
    }

    @Transactional
    public ImportApiResult importSessions(UUID userId, PublicApiSessionRequest request, ApiKey apiKey) {
        return importSessions(userId, request, apiKey != null && apiKey.isMergeSessions());
    }

    @Transactional
    public ImportApiResult importSessions(UUID userId, PublicApiSessionRequest request, boolean mergeSessions) {
        Car car = carRepository.findById(request.carId())
                .orElseThrow(() -> new IllegalArgumentException("Fahrzeug nicht gefunden"));

        // Critical ownership check
        if (!car.getUserId().equals(userId)) {
            throw new SecurityException("Dieses Fahrzeug gehört dir nicht");
        }

        // Sort chronologically so session grouping works correctly for bulk imports
        List<PublicApiSessionRequest.SessionEntry> sortedEntries = request.sessions().stream()
                .sorted((a, b) -> {
                    LocalDateTime da = parseDate(a.date());
                    LocalDateTime db = parseDate(b.date());
                    if (da == null && db == null) return 0;
                    if (da == null) return 1;
                    if (db == null) return -1;
                    return da.compareTo(db);
                })
                .toList();

        int imported = 0;
        int skipped = 0;
        int errors = 0;

        for (PublicApiSessionRequest.SessionEntry entry : sortedEntries) {
            try {
                LocalDateTime loggedAt = parseDate(entry.date());
                if (loggedAt == null) {
                    log.warn("API Upload: Ungültiges Datum '{}' — übersprungen", entry.date());
                    errors++;
                    continue;
                }

                if (isDuplicate(request.carId(), loggedAt, entry.kwh())) {
                    skipped++;
                    continue;
                }

                String geohash = parseGeohash(entry.location());
                ChargingType chargingType = parseEnum(ChargingType.class, entry.chargingType(), ChargingType.UNKNOWN);
                RouteType routeType = parseEnum(RouteType.class, entry.routeType(), null);
                TireType tireType = parseEnum(TireType.class, entry.tireType(), null);

                EvLog evLog = EvLog.createFromPublicApi(
                        request.carId(),
                        entry.kwh() != null ? BigDecimal.valueOf(entry.kwh()) : null,
                        entry.costEur() != null ? BigDecimal.valueOf(entry.costEur()) : null,
                        entry.durationMin(),
                        geohash,
                        entry.odometerKm(),
                        entry.maxChargingPowerKw() != null ? BigDecimal.valueOf(entry.maxChargingPowerKw()) : null,
                        entry.socAfter(),
                        entry.socBefore(),
                        loggedAt,
                        chargingType,
                        routeType,
                        tireType
                );

                EvLog saved = evLogRepository.save(evLog);
                coinLogService.awardCoinsForEvent(userId, CoinLogService.CoinEvent.API_UPLOAD_LOG, saved.getId());

                if (mergeSessions && sortedEntries.size() == 1) {
                    try {
                        sessionGroupService.processSessionForGrouping(saved);
                    } catch (Exception e) {
                        log.warn("Session merging failed for API upload log {}: {}", saved.getId(), e.getMessage());
                    }
                }

                imported++;

            } catch (Exception e) {
                log.warn("API Upload: Fehler beim Verarbeiten einer Session: {}", e.getMessage());
                errors++;
            }
        }

        return new ImportApiResult(imported, skipped, errors);
    }

    private boolean isDuplicate(UUID carId, LocalDateTime loggedAt, Double kwh) {
        return evLogRepository.existsByCarIdAndLoggedAtAndKwhCharged(
                carId, loggedAt, BigDecimal.valueOf(kwh));
    }

    private LocalDateTime parseDate(String raw) {
        if (raw == null) return null;
        // Try offset-aware format first — convert to UTC to stay consistent with DB storage
        try {
            return OffsetDateTime.parse(raw, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    .withOffsetSameInstant(ZoneOffset.UTC)
                    .toLocalDateTime();
        } catch (DateTimeParseException ignored) {}
        // Fall back to naive formats (assumed to be local/UTC — no offset info)
        for (DateTimeFormatter fmt : DATE_FORMATTERS) {
            try {
                return LocalDateTime.parse(raw, fmt);
            } catch (DateTimeParseException ignored) {}
        }
        return null;
    }

    private String parseGeohash(String location) {
        if (location == null || location.isBlank()) return null;
        Matcher m = LAT_LON_PATTERN.matcher(location.trim());
        if (!m.matches()) return null;
        try {
            double lat = Double.parseDouble(m.group(1));
            double lon = Double.parseDouble(m.group(2));
            return GeoHash.geoHashStringWithCharacterPrecision(lat, lon, 5);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private <T extends Enum<T>> T parseEnum(Class<T> clazz, String value, T defaultValue) {
        if (value == null || value.isBlank()) return defaultValue;
        try {
            return Enum.valueOf(clazz, value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return defaultValue;
        }
    }
}
