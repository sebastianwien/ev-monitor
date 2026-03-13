package com.evmonitor.application.teslalogger;

import ch.hsr.geohash.GeoHash;
import com.evmonitor.application.CoinLogService;
import com.evmonitor.application.spritmonitor.ImportResult;
import com.evmonitor.domain.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class TeslaLoggerImportService {

    private static final DataSource DATA_SOURCE = DataSource.TESLA_LOGGER_IMPORT;

    /**
     * Ordered list of date formats to try — most specific first.
     * Handles ISO, European (DD.MM.YYYY), US (MM/DD/YYYY), and date-only variants.
     */
    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ISO_OFFSET_DATE_TIME,                // 2025-08-20T10:56:48+02:00
            DateTimeFormatter.ISO_DATE_TIME,                       // 2025-08-20T10:56:48
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),    // 2025-08-20 10:56:48
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),       // 2025-08-20 10:56
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"),    // 20.08.2025 10:56:48
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"),       // 20.08.2025 10:56
            DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss"),    // 08/20/2025 10:56:48
            DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm"),       // 08/20/2025 10:56
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),             // 2025-08-20
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),             // 20.08.2025
            DateTimeFormatter.ofPattern("MM/dd/yyyy")              // 08/20/2025
    );

    /**
     * Matches lat/lon in various separator styles:
     * 48.2082,16.3738  |  48.2082, 16.3738  |  48.2082;16.3738  |  48.2082 16.3738
     */
    private static final Pattern LAT_LON_PATTERN = Pattern.compile(
            "^(-?\\d{1,3}\\.\\d+)[,;\\s]+\\s*(-?\\d{1,3}\\.\\d+)$"
    );

    private final EvLogRepository evLogRepository;
    private final CarRepository carRepository;
    private final CoinLogService coinLogService;
    private final ObjectMapper objectMapper;

    public TeslaLoggerImportService(EvLogRepository evLogRepository, CarRepository carRepository,
                                    CoinLogService coinLogService, ObjectMapper objectMapper) {
        this.evLogRepository = evLogRepository;
        this.carRepository = carRepository;
        this.coinLogService = coinLogService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ImportResult importData(UUID userId, UUID carId, String format, String data) {
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new IllegalArgumentException("Fahrzeug nicht gefunden"));
        if (!car.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Dieses Fahrzeug gehört dir nicht");
        }

        List<Map<String, String>> rows;
        try {
            rows = "json".equalsIgnoreCase(format) ? parseJson(data) : parseCsv(data);
        } catch (Exception e) {
            ImportResult result = new ImportResult();
            result.addError("Datei konnte nicht gelesen werden: " + e.getMessage());
            return result;
        }

        ImportResult result = new ImportResult();
        for (int i = 0; i < rows.size(); i++) {
            int rowNum = i + 2; // 1-based + header row
            try {
                processRow(rows.get(i), userId, carId, rowNum, result);
            } catch (Exception e) {
                log.error("Row {}: import failed — {}", rowNum, e.getMessage());
                result.addError("Zeile " + rowNum + ": " + e.getMessage());
            }
        }

        // One-time bonus for first-ever TeslaLogger import (idempotency enforced by awardCoinsForEvent)
        if (result.getImported() > 0) {
            result.addCoinsAwarded(coinLogService.awardCoinsForEvent(userId, CoinLogService.CoinEvent.TESLA_LOGGER_CONNECTED, null));
        }

        return result;
    }

    private void processRow(Map<String, String> row, UUID userId, UUID carId, int rowNum, ImportResult result) {
        String rawDate = get(row, "date");
        String rawOdometer = get(row, "odometer_km");
        String rawKwh = get(row, "kwh");
        String rawSocBefore = get(row, "soc_before");
        String rawSocAfter = get(row, "soc_after");

        if (rawDate == null)     { result.addError("Zeile " + rowNum + ": 'date' fehlt");         result.incrementSkipped(); return; }
        if (rawOdometer == null) { result.addError("Zeile " + rowNum + ": 'odometer_km' fehlt");  result.incrementSkipped(); return; }
        if (rawKwh == null)      { result.addError("Zeile " + rowNum + ": 'kwh' fehlt");           result.incrementSkipped(); return; }
        if (rawSocBefore == null && rawSocAfter == null) {
            result.addError("Zeile " + rowNum + ": 'soc_before' oder 'soc_after' fehlt");
            result.incrementSkipped();
            return;
        }

        LocalDateTime loggedAt = parseDate(rawDate);
        if (loggedAt == null) {
            result.addError("Zeile " + rowNum + ": Datum '" + rawDate + "' konnte nicht gelesen werden");
            result.incrementSkipped();
            return;
        }

        BigDecimal kwh = parseBigDecimal(rawKwh);
        if (kwh == null || kwh.compareTo(BigDecimal.ZERO) <= 0) {
            result.addError("Zeile " + rowNum + ": 'kwh' muss größer 0 sein");
            result.incrementSkipped();
            return;
        }

        BigDecimal odometerRaw = parseBigDecimal(rawOdometer);
        if (odometerRaw == null || odometerRaw.compareTo(BigDecimal.ZERO) <= 0) {
            result.addError("Zeile " + rowNum + ": 'odometer_km' muss größer 0 sein");
            result.incrementSkipped();
            return;
        }

        // soc_after is preferred for consumption calculations; fall back to soc_before
        Integer socAfter = parseInteger(rawSocAfter);
        Integer socForLog = socAfter != null ? socAfter : parseInteger(rawSocBefore);

        BigDecimal costEur = parseBigDecimal(get(row, "cost_eur"));
        Integer durationMin = parseInteger(get(row, "duration_min"));
        String geohash = parseLocation(get(row, "location"));
        ChargingType chargingType = parseChargingType(get(row, "charging_type"));

        // Duplicate check: any log within ±1h for the same car
        if (evLogRepository.existsByCarIdAndLoggedAtBetween(carId, loggedAt.minusHours(1), loggedAt.plusHours(1))) {
            log.debug("Row {}: duplicate detected near {}, skipping", rowNum, loggedAt);
            result.incrementSkipped();
            return;
        }

        EvLog savedLog = evLogRepository.save(EvLog.createNewWithSource(
                carId, kwh, costEur, durationMin, geohash, odometerRaw.intValue(),
                null, socForLog, loggedAt, DATA_SOURCE, chargingType, null
        ));
        result.incrementImported();

        // Award 2 coins per imported log, linked for deletion deduction
        coinLogService.awardCoinsForEvent(userId, CoinLogService.CoinEvent.TESLA_HISTORY_LOG, savedLog.getId());
        result.addCoinsAwarded(CoinLogService.CoinEvent.TESLA_HISTORY_LOG.getDefaultAmount());
    }

    // --- Parsers (package-private for tests) ---

    LocalDateTime parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim();

        // Unix timestamp in seconds (9–11 digits)
        if (s.matches("\\d{9,11}")) {
            try { return LocalDateTime.ofEpochSecond(Long.parseLong(s), 0, ZoneOffset.UTC); }
            catch (NumberFormatException ignored) {}
        }
        // Unix timestamp in milliseconds (12–14 digits)
        if (s.matches("\\d{12,14}")) {
            try { return LocalDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(s)), ZoneOffset.UTC); }
            catch (NumberFormatException ignored) {}
        }

        for (DateTimeFormatter fmt : DATE_FORMATTERS) {
            try {
                // Offset-aware: convert to UTC for consistent storage
                return OffsetDateTime.parse(s, fmt).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
            } catch (DateTimeParseException ignored) {}
            try {
                return LocalDateTime.parse(s, fmt);
            } catch (DateTimeParseException ignored) {}
            try {
                // Date-only formatters produce LocalDate — use start of day
                return java.time.LocalDate.parse(s, fmt).atStartOfDay();
            } catch (DateTimeParseException ignored) {}
        }
        return null;
    }

    String parseLocation(String raw) {
        if (raw == null || raw.isBlank()) return null;
        Matcher m = LAT_LON_PATTERN.matcher(raw.trim());
        if (m.matches()) {
            try {
                double lat = Double.parseDouble(m.group(1));
                double lon = Double.parseDouble(m.group(2));
                if (lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180) {
                    return GeoHash.withCharacterPrecision(lat, lon, 5).toBase32();
                }
            } catch (NumberFormatException ignored) {}
        }
        // Place name: no geocoding available here — heatmap won't show this entry
        log.debug("Location '{}' is a place name, geohash skipped", raw);
        return null;
    }

    // --- CSV / JSON ---

    private List<Map<String, String>> parseCsv(String data) {
        String[] lines = data.strip().split("\\r?\\n");
        if (lines.length < 2) return List.of();

        String[] headers = splitCsvLine(lines[0]);
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            String[] values = splitCsvLine(line);
            Map<String, String> row = new LinkedHashMap<>();
            for (int j = 0; j < headers.length; j++) {
                String value = j < values.length ? values[j].trim() : "";
                if (!value.isEmpty()) row.put(headers[j].trim().toLowerCase(), value);
            }
            rows.add(row);
        }
        return rows;
    }

    private String[] splitCsvLine(String line) {
        List<String> result = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();
        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        result.add(current.toString());
        return result.toArray(new String[0]);
    }

    private List<Map<String, String>> parseJson(String data) throws Exception {
        List<Map<String, Object>> rawList = objectMapper.readValue(data, new TypeReference<>() {});
        List<Map<String, String>> result = new ArrayList<>();
        for (Map<String, Object> raw : rawList) {
            Map<String, String> row = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : raw.entrySet()) {
                if (entry.getValue() != null) {
                    row.put(entry.getKey().toLowerCase(), entry.getValue().toString());
                }
            }
            result.add(row);
        }
        return result;
    }

    // --- Helpers ---

    private String get(Map<String, String> row, String key) {
        String value = row.get(key);
        return (value == null || value.isBlank()) ? null : value;
    }

    private BigDecimal parseBigDecimal(String raw) {
        if (raw == null) return null;
        try { return new BigDecimal(raw.replace(",", ".").trim()); }
        catch (NumberFormatException e) { return null; }
    }

    private Integer parseInteger(String raw) {
        if (raw == null) return null;
        try { return new BigDecimal(raw.trim()).intValue(); }
        catch (NumberFormatException e) { return null; }
    }

    ChargingType parseChargingType(String raw) {
        if (raw == null) return ChargingType.UNKNOWN;
        try { return ChargingType.valueOf(raw.trim().toUpperCase()); }
        catch (IllegalArgumentException e) { return ChargingType.UNKNOWN; }
    }
}
