package com.evmonitor.application.manualimport;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns raw CSV or JSON upload text into lower-cased key/value rows and offers
 * lenient scalar parsing. Shared by the session and trip imports so both accept
 * exactly the same file conventions (quote-aware CSV, decimal comma, empty cells dropped).
 */
@Component
@RequiredArgsConstructor
public class ImportRowParser {

    private final ObjectMapper objectMapper;

    public record ParsedRows(List<Map<String, String>> rows, int columnMismatches) {}

    /** Parses according to {@code format} ("json" or anything else = CSV). Throws on malformed input. */
    public ParsedRows parse(String format, String data) throws Exception {
        if ("json".equalsIgnoreCase(format)) {
            return new ParsedRows(parseJson(data), 0);
        }
        return parseCsvWithWarnings(data);
    }

    public ParsedRows parseCsvWithWarnings(String data) {
        String[] lines = data.strip().split("\\r?\\n");
        if (lines.length < 2) return new ParsedRows(List.of(), 0);

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
        return new ParsedRows(rows, columnMismatches);
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

    public List<Map<String, String>> parseJson(String data) throws Exception {
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

    // --- Scalar helpers ---

    public String get(Map<String, String> row, String key) {
        String value = row.get(key);
        return (value == null || value.isBlank()) ? null : value;
    }

    public Integer parseInteger(String raw) {
        if (raw == null) return null;
        try { return (int) Double.parseDouble(raw.replace(",", ".").trim()); }
        catch (NumberFormatException e) { return null; }
    }

    public Double parseDouble(String raw) {
        if (raw == null) return null;
        try { return Double.parseDouble(raw.replace(",", ".").trim()); }
        catch (NumberFormatException e) { return null; }
    }

    public BigDecimal parseBigDecimal(String raw) {
        if (raw == null) return null;
        try { return new BigDecimal(raw.replace(",", ".").trim()); }
        catch (NumberFormatException e) { return null; }
    }

    public Boolean parseBoolean(String raw) {
        if (raw == null) return null;
        String v = raw.trim();
        return "true".equalsIgnoreCase(v) || "1".equals(v);
    }
}
