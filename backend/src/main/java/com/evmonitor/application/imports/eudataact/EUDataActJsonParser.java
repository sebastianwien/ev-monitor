package com.evmonitor.application.imports.eudataact;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Liest einen EU-Data-Act-Export und erkennt die Ladevorgaenge.
 * <p>
 * VW liefert je nach Fahrzeugplattform unterschiedliche Formate - der passende
 * {@link SessionDetector} wird pro Datei ermittelt. Die Datei wird gestreamt gelesen:
 * ein Objektbaum ueber 20+ MB JSON kostet mehrere hundert MB Heap.
 */
@Component
@Slf4j
public class EUDataActJsonParser {

    /** Reihenfolge = Prioritaet: die Variante mit echter Ladeleistung gewinnt. */
    private static final List<SessionDetector> DETECTORS =
            List.of(new ChargingStateSessionDetector(), new SocCurveSessionDetector());

    private final ObjectMapper objectMapper;

    public EUDataActJsonParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public EUDataActParseResult parse(InputStream json) throws IOException {
        String vin = null;
        List<DataEntry> entries = new ArrayList<>();

        try (JsonParser parser = objectMapper.getFactory().createParser(json)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new IllegalArgumentException("Kein gueltiger EU-Data-Act-Export");
            }
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                String name = parser.currentName();
                parser.nextToken();
                if ("vin".equals(name)) {
                    vin = parser.getValueAsString();
                } else if ("Data".equals(name)) {
                    readEntries(parser, entries);
                } else {
                    parser.skipChildren();
                }
            }
        }

        EntryIndex index = EntryIndex.of(entries);
        SessionDetector detector = DETECTORS.stream()
                .filter(d -> d.supports(index))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Format wird nicht unterstuetzt - die Datei enthaelt keine erkennbaren Ladedaten"));

        log.debug("EU Data Act: {} Eintraege, Detektor {}", entries.size(),
                detector.getClass().getSimpleName());

        return new EUDataActParseResult(vin, detector.detect(index));
    }

    private void readEntries(JsonParser parser, List<DataEntry> out) throws IOException {
        if (parser.currentToken() != JsonToken.START_ARRAY) return;

        while (parser.nextToken() == JsonToken.START_OBJECT) {
            String field = null;
            String value = null;
            String timestamp = null;

            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                String name = parser.currentName();
                parser.nextToken();
                switch (name) {
                    case "dataFieldName" -> field = parser.getValueAsString();
                    case "value" -> value = parser.getValueAsString();
                    case "timestampUtc" -> timestamp = parser.getValueAsString();
                    default -> parser.skipChildren();
                }
            }

            if (field == null || value == null) continue;
            OffsetDateTime ts = parseTimestamp(timestamp);
            if (ts != null) out.add(new DataEntry(field, value, ts));
        }
    }

    // --- Timestamp-Parsing ---

    private OffsetDateTime parseTimestamp(String raw) {
        if (raw == null || raw.isBlank() || "N/A".equals(raw)) return null;
        try {
            String s = truncateToMillis(raw.trim().replace(" ", "T"));
            if (s.endsWith("Z")) s = s.substring(0, s.length() - 1) + "+00:00";
            // Ohne Offset gilt UTC
            if (!s.contains("+") && !s.matches(".*T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?-\\d{2}:\\d{2}$")) {
                s += "+00:00";
            }
            return OffsetDateTime.parse(s);
        } catch (Exception ignored) {
            return null;
        }
    }

    /** Kuerzt Sub-Millisekunden: "13:23:02.984294250" -> "13:23:02.984" */
    private String truncateToMillis(String s) {
        int dotIdx = s.indexOf('.');
        if (dotIdx < 0) return s;
        int end = dotIdx + 1;
        while (end < s.length() && Character.isDigit(s.charAt(end))) end++;
        if (end - dotIdx - 1 > 3) return s.substring(0, dotIdx + 4) + s.substring(end);
        return s;
    }
}
