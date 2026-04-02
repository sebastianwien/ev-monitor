package com.evmonitor.application.publicapi;

import ch.hsr.geohash.GeoHash;
import com.evmonitor.application.CoinLogService;
import com.evmonitor.application.SessionGroupService;
import com.evmonitor.domain.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class PublicApiImportService {

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ISO_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm")
    );

    private static final List<DateTimeFormatter> DATE_ONLY_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy")
    );

    private static final Pattern LAT_LON_PATTERN = Pattern.compile(
            "^(-?\\d{1,3}\\.\\d+)[,;\\s]+\\s*(-?\\d{1,3}\\.\\d+)$"
    );

    private final EvLogRepository evLogRepository;
    private final CarRepository carRepository;
    private final CoinLogService coinLogService;
    private final SessionGroupService sessionGroupService;
    private final CpoNameNormalizer cpoNameNormalizer;

    public PublicApiImportService(EvLogRepository evLogRepository, CarRepository carRepository,
                                  CoinLogService coinLogService, SessionGroupService sessionGroupService,
                                  CpoNameNormalizer cpoNameNormalizer) {
        this.evLogRepository = evLogRepository;
        this.carRepository = carRepository;
        this.coinLogService = coinLogService;
        this.sessionGroupService = sessionGroupService;
        this.cpoNameNormalizer = cpoNameNormalizer;
    }

    @Transactional
    public ImportApiResult importSessions(UUID userId, PublicApiSessionRequest request, ApiKey apiKey) {
        return importSessions(userId, request, apiKey != null && apiKey.isMergeSessions());
    }

    @Transactional
    public ImportApiResult importSessions(UUID userId, PublicApiSessionRequest request, boolean mergeSessions) {
        return importSessions(userId, request, mergeSessions, false, DataSource.API_UPLOAD);
    }

    @Transactional
    public ImportApiResult importSessions(UUID userId, PublicApiSessionRequest request, boolean mergeSessions, boolean allowBatchMerge) {
        return importSessions(userId, request, mergeSessions, allowBatchMerge, DataSource.API_UPLOAD);
    }

    @Transactional
    public ImportApiResult importSessions(UUID userId, PublicApiSessionRequest request, boolean mergeSessions, boolean allowBatchMerge, DataSource dataSource) {
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
        List<ImportApiResult.ImportedSession> importedResults = new ArrayList<>();
        java.util.Set<LocalDateTime> batchUsedTimestamps = new java.util.HashSet<>();

        for (PublicApiSessionRequest.SessionEntry entry : sortedEntries) {
            try {
                LocalDateTime loggedAt = parseDate(entry.date());
                if (loggedAt == null) {
                    log.warn("API Upload: Ungültiges Datum '{}' — übersprungen", entry.date());
                    errors++;
                    continue;
                }

                // If multiple entries share the same timestamp (e.g. date-only imports with several
                // sessions per day), bump each one by 10 minutes so they are distinguishable in the log feed.
                while (batchUsedTimestamps.contains(loggedAt)) {
                    loggedAt = loggedAt.plusMinutes(10);
                }
                batchUsedTimestamps.add(loggedAt);

                if (isDuplicate(request.carId(), loggedAt, entry.kwh(), dataSource)) {
                    skipped++;
                    continue;
                }

                boolean isPublic = Boolean.TRUE.equals(entry.isPublicCharging());
                String cpoName = cpoNameNormalizer.normalize(entry.cpoName());
                String geohash = parseGeohash(entry.location(), isPublic ? 7 : 6);
                ChargingType chargingType = parseEnum(ChargingType.class, entry.chargingType(), ChargingType.UNKNOWN);
                RouteType routeType = parseEnum(RouteType.class, entry.routeType(), null);
                TireType tireType = parseEnum(TireType.class, entry.tireType(), null);
                EnergyMeasurementType measurementType = parseEnum(EnergyMeasurementType.class, entry.measurementType(), null);

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
                        tireType,
                        dataSource,
                        entry.rawImportData(),
                        isPublic,
                        cpoName,
                        measurementType
                );

                EvLog saved;
                try {
                    saved = evLogRepository.save(evLog);
                } catch (DataIntegrityViolationException e) {
                    log.debug("API Upload: Duplikat beim Speichern erkannt (race condition) — übersprungen");
                    skipped++;
                    continue;
                }
                coinLogService.awardCoinsForEvent(userId, CoinLogService.CoinEvent.API_UPLOAD_LOG, saved.getId());

                if (mergeSessions && (allowBatchMerge || sortedEntries.size() == 1)) {
                    try {
                        sessionGroupService.processSessionForGrouping(saved);
                    } catch (Exception e) {
                        log.warn("Session merging failed for API upload log {}: {}", saved.getId(), e.getMessage());
                    }
                }

                importedResults.add(new ImportApiResult.ImportedSession(
                        saved.getLoggedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), saved.getId()));
                imported++;

            } catch (Exception e) {
                log.warn("API Upload: Fehler beim Verarbeiten einer Session: {}", e.getMessage());
                errors++;
            }
        }

        return new ImportApiResult(imported, skipped, errors, 0, importedResults);
    }

    @Transactional
    public void patchApiSession(UUID userId, UUID logId, PatchSessionRequest patch) {
        EvLog existing = evLogRepository.findById(logId)
                .orElseThrow(() -> new NoSuchElementException("Log nicht gefunden"));

        if (existing.getDataSource() != DataSource.API_UPLOAD) {
            throw new IllegalArgumentException("Nur via Public API importierte Logs können über diesen Endpoint aktualisiert werden");
        }

        Car car = carRepository.findById(existing.getCarId())
                .orElseThrow(() -> new IllegalArgumentException("Fahrzeug nicht gefunden"));
        if (!car.getUserId().equals(userId)) {
            throw new SecurityException("Kein Zugriff auf diesen Log");
        }

        boolean isPublic = patch.isPublicCharging() != null ? patch.isPublicCharging() : existing.isPublicCharging();
        String geohash = patch.location() != null
                ? parseGeohash(patch.location(), isPublic ? 7 : 6)
                : existing.getGeohash();
        String cpoName = patch.cpoName() != null
                ? cpoNameNormalizer.normalize(patch.cpoName())
                : existing.getCpoName();

        ChargingType chargingType = patch.chargingType() != null
                ? parseEnum(ChargingType.class, patch.chargingType(), null)
                : null;
        RouteType routeType = patch.routeType() != null
                ? parseEnum(RouteType.class, patch.routeType(), null)
                : null;
        TireType tireType = patch.tireType() != null
                ? parseEnum(TireType.class, patch.tireType(), null)
                : null;
        EnergyMeasurementType measurementType = patch.measurementType() != null
                ? parseEnum(EnergyMeasurementType.class, patch.measurementType(), null)
                : null;

        EvLog patched = existing.withPatch(
                patch.kwh() != null ? BigDecimal.valueOf(patch.kwh()) : null,
                patch.costEur() != null ? BigDecimal.valueOf(patch.costEur()) : null,
                patch.durationMin(),
                geohash,
                patch.odometerKm(),
                patch.socBefore(),
                patch.socAfter(),
                patch.maxChargingPowerKw() != null ? BigDecimal.valueOf(patch.maxChargingPowerKw()) : null,
                chargingType, routeType, tireType,
                isPublic, cpoName, measurementType
        );

        evLogRepository.save(patched);
    }

    public ApiSessionResponse getSession(UUID userId, UUID logId) {
        EvLog existing = evLogRepository.findById(logId)
                .orElseThrow(() -> new NoSuchElementException("Log nicht gefunden"));

        if (existing.getDataSource() != DataSource.API_UPLOAD) {
            throw new IllegalArgumentException("Nur via Public API importierte Logs können über diesen Endpoint abgefragt werden");
        }

        Car car = carRepository.findById(existing.getCarId())
                .orElseThrow(() -> new IllegalArgumentException("Fahrzeug nicht gefunden"));
        if (!car.getUserId().equals(userId)) {
            throw new SecurityException("Kein Zugriff auf diesen Log");
        }

        return ApiSessionResponse.fromEvLog(existing);
    }

    private boolean isDuplicate(UUID carId, LocalDateTime loggedAt, Double kwh, DataSource dataSource) {
        return evLogRepository.existsByCarIdAndLoggedAtAndDataSource(carId, loggedAt.withSecond(0).withNano(0), dataSource);
    }

    private LocalDateTime parseDate(String raw) {
        if (raw == null) return null;
        // Try offset-aware format first — convert to UTC to stay consistent with DB storage
        try {
            return OffsetDateTime.parse(raw, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    .withOffsetSameInstant(ZoneOffset.UTC)
                    .toLocalDateTime();
        } catch (DateTimeParseException ignored) {}
        // Try datetime formats (include time component)
        for (DateTimeFormatter fmt : DATE_FORMATTERS) {
            try {
                return LocalDateTime.parse(raw, fmt);
            } catch (DateTimeParseException ignored) {}
        }
        // Try date-only formats (no time component — use start of day)
        for (DateTimeFormatter fmt : DATE_ONLY_FORMATTERS) {
            try {
                return LocalDate.parse(raw, fmt).atStartOfDay();
            } catch (DateTimeParseException ignored) {}
        }
        return null;
    }

    private String parseGeohash(String location, int precision) {
        if (location == null || location.isBlank()) return null;
        Matcher m = LAT_LON_PATTERN.matcher(location.trim());
        if (!m.matches()) return null;
        try {
            double lat = Double.parseDouble(m.group(1));
            double lon = Double.parseDouble(m.group(2));
            return GeoHash.geoHashStringWithCharacterPrecision(lat, lon, precision);
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
