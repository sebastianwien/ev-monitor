package com.evmonitor.application.imports.eudataact;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Historien-Export ("Request File" im Portal): VW liefert fertige Ladevorgaenge als
 * {@code chargingSession.[n].*}-Records mit gemessenen kWh. Die bevorzugte Variante -
 * nichts wird integriert oder geschaetzt.
 * <p>
 * Die Records tragen ihre Zeitstempel als Werte; das {@code timestampUtc} der Eintraege ist
 * dort unbrauchbar. Der Kilometerstand steht nicht im Record, sondern als Telemetrie-Signal
 * {@value #F_MILEAGE} (Data-Dictionary: "mileage", km) in derselben Datei.
 */
class ChargingSessionRecordDetector implements SessionDetector {

    static final String PREFIX = "chargingSession.";
    /** Data-Dictionary-ID des Kilometerstands im Historien-Export. */
    static final String F_MILEAGE = "180876";

    private static final Pattern RECORD = Pattern.compile("^chargingSession\\.\\[(\\d+)]\\.(.+)$");

    @Override
    public boolean mightSupport(Set<String> fieldNames) {
        return fieldNames.stream().anyMatch(f -> f.startsWith(PREFIX));
    }

    @Override
    public boolean accepts(String field) {
        return field.startsWith(PREFIX) || F_MILEAGE.equals(field);
    }

    @Override
    public boolean supports(EntryIndex index) {
        return index.fields().stream().anyMatch(f -> f.startsWith(PREFIX));
    }

    @Override
    public List<EUDataActSession> detect(EntryIndex index) {
        Map<Integer, Map<String, String>> records = new TreeMap<>();
        for (String field : index.fields()) {
            Matcher m = RECORD.matcher(field);
            if (!m.matches()) continue;
            // Pro Feld genau ein Wert; bei Wiederholung gewinnt der letzte
            List<DataEntry> values = index.get(field);
            records.computeIfAbsent(Integer.parseInt(m.group(1)), k -> new HashMap<>())
                    .put(m.group(2), values.get(values.size() - 1).value().trim());
        }

        return records.values().stream()
                .map(r -> toSession(r, index))
                .filter(s -> s != null)
                .sorted(Comparator.comparing(EUDataActSession::startedAt))
                .toList();
    }

    private EUDataActSession toSession(Map<String, String> r, EntryIndex index) {
        OffsetDateTime start = timestamp(r.get("startChargingTimestamp"));
        OffsetDateTime end = timestamp(r.get("stopChargingTimestamp"));
        Double kwh = number(r.get("totalEnergyCharged"));
        if (start == null || end == null || kwh == null || kwh <= 0) return null;

        Double activeSeconds = number(r.get("activeChargingTime"));
        int durationMin = activeSeconds != null
                ? (int) Math.round(activeSeconds / 60.0)
                : (int) Duration.between(start, end).toMinutes();

        return new EUDataActSession(
                start,
                end,
                durationMin,
                integer(r.get("startSoc")),
                integer(r.get("endSoc")),
                null, // kWh sind gemessen - kein SoC-Fallback noetig
                chargeType(r.get("chargeType")),
                number(r.get("peakChargePower")),
                kwh,
                index.lastAt(F_MILEAGE, start).map(e -> (int) e.asDouble()).orElse(null),
                null); // keine Aussentemperatur im Record
    }

    private static String chargeType(String raw) {
        return raw == null ? null : switch (raw.toUpperCase()) {
            case "AC", "DC" -> raw.toUpperCase();
            default -> null;
        };
    }

    private static OffsetDateTime timestamp(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return OffsetDateTime.parse(raw);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static Double number(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer integer(String raw) {
        Double d = number(raw);
        return d == null ? null : (int) Math.round(d);
    }
}
