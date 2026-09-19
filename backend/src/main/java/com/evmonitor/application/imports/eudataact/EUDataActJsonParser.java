package com.evmonitor.application.imports.eudataact;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamSource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Liest einen EU-Data-Act-Export und erkennt die Ladevorgaenge.
 * <p>
 * VW liefert je nach Fahrzeugplattform und Anfrageart unterschiedliche Formate - der passende
 * {@link SessionDetector} wird pro Datei ermittelt. Exporte erreichen Hunderte MB (der
 * Historien-Export ueber 1 Mio. Eintraege), deshalb wird zweimal gestreamt: erst nur die
 * Feldnamen, dann - fuer den ersten Detektor, der in Frage kommt - nur dessen Eintraege.
 */
@Component
@Slf4j
public class EUDataActJsonParser {

    /** Reihenfolge = Prioritaet: gemessene Records vor Leistungssignal vor SoC-Verlauf. */
    private static final List<SessionDetector> DETECTORS = List.of(
            new ChargingSessionRecordDetector(),
            new ChargingStateSessionDetector(),
            new SocCurveSessionDetector());

    private final ObjectMapper objectMapper;

    public EUDataActJsonParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Fuer kleine, bereits im Speicher liegende Exporte (Tests). */
    public EUDataActParseResult parse(InputStream json) throws IOException {
        byte[] bytes = json.readAllBytes();
        return parse(() -> new ByteArrayInputStream(bytes));
    }

    public EUDataActParseResult parse(InputStreamSource source) throws IOException {
        return parse(source, false);
    }

    /**
     * {@code lenient} fuer den AutoSync: dort ist eine Datei ohne Ladedaten der Normalfall - das
     * Fahrzeug meldet im 15-Minuten-Feed auch dann Telemetrie, wenn es tagelang steht. Sie als
     * Fehler zu behandeln wuerde denselben Datensatz bei jedem Poll erneut scheitern lassen.
     * Beim manuellen Upload bleibt es ein Fehler, sonst bekaeme der Nutzer keine Rueckmeldung.
     * Eine kaputte oder fremde Datei wirft in beiden Faellen.
     */
    public EUDataActParseResult parse(InputStreamSource source, boolean lenient) throws IOException {
        Header header = readHeader(source);

        for (SessionDetector detector : DETECTORS) {
            if (!detector.mightSupport(header.fieldNames())) continue;
            EntryIndex index = EntryIndex.of(readEntries(source, detector::accepts));
            if (!detector.supports(index)) continue;

            log.debug("EU Data Act: {} Felder, Detektor {}", header.fieldNames().size(),
                    detector.getClass().getSimpleName());
            return new EUDataActParseResult(header.vin(), detector.detect(index));
        }
        if (lenient) {
            log.debug("EU Data Act: kein Detektor greift ({} Felder) - keine Ladedaten in dieser Datei",
                    header.fieldNames().size());
            return new EUDataActParseResult(header.vin(), List.of());
        }
        throw new IllegalArgumentException(
                "Format wird nicht unterstuetzt - die Datei enthaelt keine erkennbaren Ladedaten");
    }

    private record Header(String vin, Set<String> fieldNames) {}

    /** Echte Exporte haben ~13.000 verschiedene Feldnamen; darueber ist es kein Export mehr. */
    static final int MAX_DISTINCT_FIELDS = 100_000;

    /** Pass 1: VIN und die Menge der Feldnamen - ohne Werte zu behalten. */
    private Header readHeader(InputStreamSource source) throws IOException {
        Set<String> fieldNames = new HashSet<>();
        String[] vin = new String[1];
        scan(source, vin, entry -> {
            if (fieldNames.add(entry.field()) && fieldNames.size() > MAX_DISTINCT_FIELDS) {
                throw new IllegalArgumentException("Kein gueltiger EU-Data-Act-Export (zu viele Felder)");
            }
        }, field -> true, false);
        return new Header(vin[0], fieldNames);
    }

    /** Pass 2: nur die Eintraege, die der Detektor anfordert. */
    private List<DataEntry> readEntries(InputStreamSource source, Predicate<String> accept) throws IOException {
        List<DataEntry> entries = new ArrayList<>();
        scan(source, new String[1], entries::add, accept, true);
        return entries;
    }

    private void scan(InputStreamSource source, String[] vinOut, java.util.function.Consumer<DataEntry> sink,
                      Predicate<String> accept, boolean withValues) throws IOException {
        try (InputStream in = source.getInputStream();
             JsonParser parser = objectMapper.getFactory().createParser(in)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new IllegalArgumentException("Kein gueltiger EU-Data-Act-Export");
            }
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                String name = parser.currentName();
                parser.nextToken();
                if ("vin".equals(name)) {
                    vinOut[0] = parser.getValueAsString();
                } else if ("Data".equals(name)) {
                    readDataArray(parser, sink, accept, withValues);
                } else {
                    parser.skipChildren();
                }
            }
        }
    }

    private void readDataArray(JsonParser parser, java.util.function.Consumer<DataEntry> sink,
                               Predicate<String> accept, boolean withValues) throws IOException {
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
                    case "value" -> { if (withValues) value = parser.getValueAsString(); }
                    case "timestampUtc" -> { if (withValues) timestamp = parser.getValueAsString(); }
                    default -> parser.skipChildren();
                }
            }

            if (field == null || !accept.test(field)) continue;
            if (!withValues) {
                sink.accept(new DataEntry(field, "", null));
                continue;
            }
            if (value == null) continue;
            OffsetDateTime ts = parseTimestamp(timestamp);
            if (ts != null) sink.accept(new DataEntry(field, value, ts));
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
