package com.evmonitor.application.publicapi;

import ch.hsr.geohash.GeoHash;
import com.evmonitor.application.ingest.ChargingEntry;
import com.evmonitor.application.ingest.IngestCommand;
import com.evmonitor.application.ingest.IngestDoor;
import com.evmonitor.application.ingest.IngestGateway;
import com.evmonitor.application.ingest.IngestResult;
import com.evmonitor.domain.*;
import com.evmonitor.domain.exception.ForbiddenException;
import com.evmonitor.domain.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@RequiredArgsConstructor
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
    private final CpoNameNormalizer cpoNameNormalizer;
    private final com.evmonitor.application.EvLogService evLogService;
    private final IngestGateway ingestGateway;

    @Transactional
    public ImportApiResult importSessions(UUID userId, PublicApiSessionRequest request, ApiKey apiKey) {
        return importSessions(userId, request, DataSource.API_UPLOAD);
    }

    /**
     * Tür 1: parst Datum, Ort und Enums und übergibt an das {@link IngestGateway}. Einträge mit
     * ungültigem Datum zählen als Fehler; alle Regeln (Bump, Dedup, Tronity, Coins) stehen in der Policy.
     */
    @Transactional
    public ImportApiResult importSessions(UUID userId, PublicApiSessionRequest request, DataSource dataSource) {
        int dateErrors = 0;
        List<ChargingEntry> entries = new ArrayList<>();
        for (PublicApiSessionRequest.SessionEntry entry : request.sessions()) {
            LocalDateTime loggedAt = parseDate(entry.date());
            if (loggedAt == null) {
                log.warn("API Upload: Ungültiges Datum '{}' - übersprungen", entry.date());
                dateErrors++;
                continue;
            }
            entries.add(toChargingEntry(entry, loggedAt));
        }

        IngestResult result;
        try {
            result = ingestGateway.ingestCharging(new IngestCommand(
                    userId, request.carId(), dataSource, IngestDoor.IMPORT_BATCH, entries));
        } catch (NotFoundException e) {
            throw new IllegalArgumentException("Fahrzeug nicht gefunden");
        } catch (ForbiddenException e) {
            throw new SecurityException("Dieses Fahrzeug gehört dir nicht");
        }

        List<ImportApiResult.ImportedSession> importedResults = result.created().stream()
                .map(saved -> new ImportApiResult.ImportedSession(
                        saved.getLoggedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), saved.getId()))
                .toList();
        return new ImportApiResult(result.imported(), result.skipped(), result.errors() + dateErrors, 0, importedResults,
                result.skippedDeleted());
    }

    private ChargingEntry toChargingEntry(PublicApiSessionRequest.SessionEntry entry, LocalDateTime loggedAt) {
        boolean isPublic = Boolean.TRUE.equals(entry.isPublicCharging());
        EnergyMeasurementType measurementType = parseEnum(EnergyMeasurementType.class, entry.measurementType(), null);
        // If only kwh_at_vehicle is provided, infer AT_VEHICLE so the EvLog
        // constructor doesn't fall back to the data-source default (AT_CHARGER for API_UPLOAD).
        if (measurementType == null && entry.kwhAtVehicle() != null && entry.kwh() == null) {
            measurementType = EnergyMeasurementType.AT_VEHICLE;
        }
        return ChargingEntry.builder()
                .loggedAt(loggedAt)
                .kwhCharged(entry.kwh() != null ? BigDecimal.valueOf(entry.kwh()) : null)
                .kwhAtVehicle(entry.kwhAtVehicle() != null ? BigDecimal.valueOf(entry.kwhAtVehicle()) : null)
                .measurementType(measurementType)
                .costEur(entry.costEur() != null ? BigDecimal.valueOf(entry.costEur()) : null)
                .chargeDurationMinutes(entry.durationMin())
                .geohash(parseGeohash(entry.location(), isPublic ? 7 : 6))
                .publicCharging(isPublic)
                .cpoName(cpoNameNormalizer.normalize(entry.cpoName()))
                .odometerKm(entry.odometerKm())
                .maxChargingPowerKw(entry.maxChargingPowerKw() != null ? BigDecimal.valueOf(entry.maxChargingPowerKw()) : null)
                .socBefore(entry.socBefore())
                .socAfter(entry.socAfter())
                .chargingType(parseEnum(ChargingType.class, entry.chargingType(), ChargingType.UNKNOWN))
                .routeType(parseEnum(RouteType.class, entry.routeType(), null))
                .tireType(parseEnum(TireType.class, entry.tireType(), null))
                .temperatureCelsius(entry.temperatureCelsius())
                .rawImportData(entry.rawImportData())
                .build();
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
        if (!car.isOwnedBy(userId)) {
            throw new SecurityException("Kein Zugriff auf diesen Log");
        }

        Boolean isPublic = patch.isPublicCharging() != null ? patch.isPublicCharging() : existing.getPublicCharging();
        // Die Praezision haengt an "belegt oeffentlich": unbekannt bekommt die private
        // Stufe. Ein direktes isPublic ? 7 : 6 wuerde bei NULL entpacken und knallen -
        // seit V166 ist der Ladeort dreiwertig.
        String geohash = patch.location() != null
                ? parseGeohash(patch.location(), Boolean.TRUE.equals(isPublic) ? 7 : 6)
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
                patch.kwhAtVehicle() != null ? BigDecimal.valueOf(patch.kwhAtVehicle()) : null,
                patch.maxChargingPowerKw() != null ? BigDecimal.valueOf(patch.maxChargingPowerKw()) : null,
                chargingType, routeType, tireType,
                isPublic, cpoName, measurementType,
                null, null, null, // costExchangeRate, costCurrency, chargingProviderId (public API always EUR, no tariff)
                patch.temperatureCelsius()
        );

        // ingest-bypass: PATCH einer bestehenden API-Ladung
        evLogService.save(patched);
    }

    @Transactional
    public void deleteApiSession(UUID userId, UUID logId) {
        EvLog existing = evLogRepository.findById(logId)
                .orElseThrow(() -> new NoSuchElementException("Log nicht gefunden"));

        Car car = carRepository.findById(existing.getCarId())
                .orElseThrow(() -> new IllegalArgumentException("Fahrzeug nicht gefunden"));
        if (!car.isOwnedBy(userId)) {
            throw new SecurityException("Kein Zugriff auf diesen Log");
        }

        // Delegates to EvLogService to handle coin deduction on delete
        evLogService.deleteLog(logId, userId);
    }

    /**
     * Ownership, Merge-Fenster und Feld-Zusammenfuehrung liegen komplett in EvLogService -
     * hier wird nur auf das Public-API-DTO gemappt.
     */
    @Transactional
    public ApiSessionResponse mergeApiSessions(UUID userId, UUID targetLogId, UUID sourceLogId, boolean preferSource) {
        EvLog merged = evLogService.mergeLog(targetLogId, sourceLogId, userId, preferSource);
        return ApiSessionResponse.fromEvLog(merged);
    }

    public ApiSessionsPageResponse getSessions(UUID userId, UUID carId, LocalDateTime from, LocalDateTime to, int page, int size) {
        int offset = page * size;
        List<EvLog> sessions;
        long total;

        if (carId != null) {
            Car car = carRepository.findById(carId)
                    .orElseThrow(() -> new SecurityException("Dieses Fahrzeug gehört dir nicht"));
            if (!car.isOwnedBy(userId)) {
                throw new SecurityException("Dieses Fahrzeug gehört dir nicht");
            }
            sessions = evLogRepository.findPagedByCarId(carId, from, to, size, offset);
            total = evLogRepository.countByCarIdAndDateRange(carId, from, to);
        } else {
            List<UUID> carIds = carRepository.findAllByUserId(userId).stream()
                    .map(Car::getId)
                    .toList();
            sessions = evLogRepository.findPagedByCarIds(carIds, from, to, size, offset);
            total = evLogRepository.countByCarIdsAndDateRange(carIds, from, to);
        }

        List<ApiSessionResponse> responses = sessions.stream()
                .map(ApiSessionResponse::fromEvLog)
                .toList();
        boolean hasMore = (long) offset + size < total;
        return new ApiSessionsPageResponse(responses, total, page, size, hasMore);
    }

    public ApiSessionResponse getSession(UUID userId, UUID logId) {
        EvLog existing = evLogRepository.findById(logId)
                .orElseThrow(() -> new NoSuchElementException("Log nicht gefunden"));

        if (existing.getDataSource() != DataSource.API_UPLOAD) {
            throw new IllegalArgumentException("Nur via Public API importierte Logs können über diesen Endpoint abgefragt werden");
        }

        Car car = carRepository.findById(existing.getCarId())
                .orElseThrow(() -> new IllegalArgumentException("Fahrzeug nicht gefunden"));
        if (!car.isOwnedBy(userId)) {
            throw new SecurityException("Kein Zugriff auf diesen Log");
        }

        return ApiSessionResponse.fromEvLog(existing);
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
