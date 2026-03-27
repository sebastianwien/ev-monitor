package com.evmonitor.application.manualimport;

import com.evmonitor.application.publicapi.ImportApiResult;
import com.evmonitor.application.publicapi.PublicApiImportService;
import com.evmonitor.application.publicapi.PublicApiSessionRequest;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.DataSource;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
public class ManualImportService {

    private final PublicApiImportService publicApiImportService;
    private final CarRepository carRepository;
    private final ObjectMapper objectMapper;

    public ManualImportService(PublicApiImportService publicApiImportService,
                               CarRepository carRepository,
                               ObjectMapper objectMapper) {
        this.publicApiImportService = publicApiImportService;
        this.carRepository = carRepository;
        this.objectMapper = objectMapper;
    }

    public ImportApiResult importData(UUID userId, UUID carId, String format, String data, boolean mergeSessions) {
        return importData(userId, carId, format, data, mergeSessions, DataSource.API_UPLOAD);
    }

    public ImportApiResult importData(UUID userId, UUID carId, String format, String data, boolean mergeSessions, DataSource dataSource) {
        // Ownership check before parsing
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new IllegalArgumentException("Fahrzeug nicht gefunden"));
        if (!car.getUserId().equals(userId)) {
            throw new SecurityException("Dieses Fahrzeug gehört dir nicht");
        }

        List<Map<String, String>> rows;
        int columnMismatches = 0;
        try {
            if ("json".equalsIgnoreCase(format)) {
                rows = parseJson(data);
            } else {
                ParseCsvResult csvResult = parseCsvWithWarnings(data);
                rows = csvResult.rows();
                columnMismatches = csvResult.columnMismatches();
            }
        } catch (Exception e) {
            log.warn("ManualImport: Datei konnte nicht geparst werden: {}", e.getMessage());
            return ImportApiResult.withoutIds(0, 0, 1);
        }

        if (rows.isEmpty()) {
            return ImportApiResult.withoutIds(0, 0, 0);
        }

        List<PublicApiSessionRequest.SessionEntry> entries = new ArrayList<>();
        int parseErrors = 0;
        for (Map<String, String> row : rows) {
            PublicApiSessionRequest.SessionEntry entry = mapRowToEntry(row);
            if (entry != null) {
                entries.add(entry);
            } else {
                parseErrors++;
            }
        }

        if (entries.isEmpty()) {
            return ImportApiResult.withoutIds(0, 0, parseErrors, columnMismatches);
        }

        ImportApiResult result = publicApiImportService.importSessions(userId, new PublicApiSessionRequest(carId, entries), mergeSessions, true, dataSource);
        int totalErrors = result.errors() + parseErrors;
        return ImportApiResult.withoutIds(result.imported(), result.skipped(), totalErrors, columnMismatches);
    }

    private PublicApiSessionRequest.SessionEntry mapRowToEntry(Map<String, String> row) {
        String date = get(row, "date");
        if (date == null) return null;

        String kwhRaw = get(row, "kwh");
        if (kwhRaw == null) return null;
        Double kwh;
        try {
            kwh = Double.parseDouble(kwhRaw.replace(",", "."));
        } catch (NumberFormatException e) {
            return null;
        }

        Integer odometerKm = parseInteger(get(row, "odometer_km"));
        Integer socBefore = parseInteger(get(row, "soc_before"));
        Integer socAfter = parseInteger(get(row, "soc_after"));
        Double costEur = parseDouble(get(row, "cost_eur"));
        Integer durationMin = parseInteger(get(row, "duration_min"));
        String location = get(row, "location");
        String chargingType = get(row, "charging_type");
        Double maxChargingPowerKw = parseDouble(get(row, "max_charging_power_kw"));
        String routeType = get(row, "route_type");
        String tireType = get(row, "tire_type");
        String rawImportData = get(row, "raw_import_data");
        Boolean isPublicCharging = parseBoolean(get(row, "is_public_charging"));
        String cpoName = get(row, "cpo_name");
        String measurementType = get(row, "measurement_type");

        return new PublicApiSessionRequest.SessionEntry(
                date, kwh, odometerKm, socBefore, socAfter,
                costEur, durationMin, location, chargingType,
                maxChargingPowerKw, routeType, tireType, rawImportData,
                isPublicCharging, cpoName, measurementType
        );
    }

    // --- CSV / JSON parsing (quote-aware, trimmed headers) ---

    private record ParseCsvResult(List<Map<String, String>> rows, int columnMismatches) {}

    private ParseCsvResult parseCsvWithWarnings(String data) {
        String[] lines = data.strip().split("\\r?\\n");
        if (lines.length < 2) return new ParseCsvResult(List.of(), 0);

        String[] headers = splitCsvLine(lines[0]);
        List<Map<String, String>> rows = new ArrayList<>();
        int columnMismatches = 0;
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            String[] values = splitCsvLine(line);
            if (values.length < headers.length) {
                columnMismatches++;
            }
            Map<String, String> row = new LinkedHashMap<>();
            for (int j = 0; j < headers.length; j++) {
                String value = j < values.length ? values[j].trim() : "";
                if (!value.isEmpty()) row.put(headers[j].trim().toLowerCase(), value);
            }
            rows.add(row);
        }
        return new ParseCsvResult(rows, columnMismatches);
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

    private Integer parseInteger(String raw) {
        if (raw == null) return null;
        try { return (int) Double.parseDouble(raw.replace(",", ".").trim()); }
        catch (NumberFormatException e) { return null; }
    }

    private Double parseDouble(String raw) {
        if (raw == null) return null;
        try { return Double.parseDouble(raw.replace(",", ".").trim()); }
        catch (NumberFormatException e) { return null; }
    }

    private Boolean parseBoolean(String raw) {
        if (raw == null) return null;
        String v = raw.trim();
        return "true".equalsIgnoreCase(v) || "1".equals(v);
    }
}
