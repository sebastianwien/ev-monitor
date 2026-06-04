package com.evmonitor.application.imports.eudataact;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class EUDataActJsonParser {

    private static final String CHARGING = "CHARGING";
    private static final double KELVIN_OFFSET = 273.15;

    private final ObjectMapper objectMapper;

    public EUDataActParseResult parse(InputStream json) throws IOException {
        JsonNode root = objectMapper.readTree(json);
        String vin = root.path("vin").asText(null);

        List<DataEntry> entries = new ArrayList<>();
        for (JsonNode item : root.path("Data")) {
            String field = item.path("dataFieldName").asText(null);
            String value = item.path("value").asText(null);
            String tsRaw = item.path("timestampUtc").asText(null);
            if (field == null || value == null) continue;
            OffsetDateTime ts = parseTimestamp(tsRaw);
            if (ts != null) {
                entries.add(new DataEntry(field, value, ts));
            }
        }

        List<EUDataActSession> sessions = detectSessions(entries);
        return new EUDataActParseResult(vin, sessions);
    }

    // --- Session detection ---

    private List<EUDataActSession> detectSessions(List<DataEntry> entries) {
        List<DataEntry> stateEntries = entriesFor("chargingState", entries);

        List<EUDataActSession> sessions = new ArrayList<>();
        boolean inCharging = false;
        OffsetDateTime sessionStart = null;
        OffsetDateTime lastChargingTs = null;

        for (DataEntry entry : stateEntries) {
            boolean isCharging = CHARGING.equals(entry.value());

            if (isCharging && !inCharging) {
                sessionStart = entry.timestamp();
                inCharging = true;
            } else if (!isCharging && inCharging) {
                sessions.add(buildSession(sessionStart, lastChargingTs, entries));
                inCharging = false;
            }

            if (isCharging) {
                lastChargingTs = entry.timestamp();
            }
        }

        // File ends while still charging
        if (inCharging && sessionStart != null) {
            sessions.add(buildSession(sessionStart, lastChargingTs, entries));
        }

        return sessions.stream()
                .sorted(Comparator.comparing(EUDataActSession::startedAt))
                .collect(Collectors.toList());
    }

    private EUDataActSession buildSession(OffsetDateTime start, OffsetDateTime end, List<DataEntry> all) {
        int durationMin = (int) java.time.Duration.between(start, end).toMinutes();

        Integer socBefore = nearestIntBefore("currentSOCInPct", start, all);
        Integer socAfter = nearestIntAfter("currentSOCInPct", end, all);
        String chargeType = dominantChargeType(start, end, all);
        Double maxPower = maxPowerKw(start, end, all);
        Double kwh = integrateKwh(start, end, all);
        Integer odometer = nearestOdometer(start, end, all);
        Double tempCelsius = nearestTemperature(start, end, all);

        return new EUDataActSession(start, end, durationMin, socBefore, socAfter,
                chargeType, maxPower, kwh, odometer, tempCelsius);
    }

    // --- kWh integration (trapezoid, smart gap filling) ---

    private Double integrateKwh(OffsetDateTime start, OffsetDateTime end, List<DataEntry> all) {
        List<DataEntry> powerReadings = entriesFor("chargePowerInKW", all).stream()
                .filter(e -> !e.timestamp().isBefore(start) && !e.timestamp().isAfter(end))
                .collect(Collectors.toList());

        if (powerReadings.size() < 2) return null;

        double kwh = 0.0;
        for (int i = 1; i < powerReadings.size(); i++) {
            DataEntry prev = powerReadings.get(i - 1);
            DataEntry curr = powerReadings.get(i);

            if (hasStopSignal(prev.timestamp(), curr.timestamp(), all)) continue;

            double p1 = Math.max(0.0, parseDouble(prev.value()));
            double p2 = Math.max(0.0, parseDouble(curr.value()));
            double deltaHours = java.time.Duration.between(prev.timestamp(), curr.timestamp()).toSeconds() / 3600.0;

            kwh += (p1 + p2) / 2.0 * deltaHours;
        }

        return kwh > 0 ? kwh : null;
    }

    // A gap has a stop signal if any entry in that window indicates charging stopped or car moved
    private boolean hasStopSignal(OffsetDateTime from, OffsetDateTime to, List<DataEntry> all) {
        return all.stream()
                .filter(e -> e.timestamp().isAfter(from) && e.timestamp().isBefore(to))
                .anyMatch(e -> isStopSignal(e) || isMileageChange(e, from, to, all));
    }

    private boolean isStopSignal(DataEntry e) {
        return ("chargingState".equals(e.field()) && !CHARGING.equals(e.value()))
                || ("plugConnectionState".equals(e.field()) && "disconnected".equals(e.value()));
    }

    // True if a mileage reading within the gap differs from the mileage just before the gap
    private boolean isMileageChange(DataEntry e, OffsetDateTime from, OffsetDateTime to, List<DataEntry> all) {
        if (!"mileage".equals(e.field())) return false;
        List<DataEntry> mileageBefore = entriesFor("mileage", all).stream()
                .filter(m -> !m.timestamp().isAfter(from))
                .collect(Collectors.toList());
        if (mileageBefore.isEmpty()) return false;
        String lastMileage = mileageBefore.get(mileageBefore.size() - 1).value();
        return !lastMileage.equals(e.value());
    }

    // --- Field helpers ---

    private String dominantChargeType(OffsetDateTime start, OffsetDateTime end, List<DataEntry> all) {
        Map<String, Long> counts = entriesFor("chargeType", all).stream()
                .filter(e -> !e.timestamp().isBefore(start) && !e.timestamp().isAfter(end))
                .filter(e -> !"INVALID".equals(e.value()))
                .collect(Collectors.groupingBy(DataEntry::value, Collectors.counting()));
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private Double maxPowerKw(OffsetDateTime start, OffsetDateTime end, List<DataEntry> all) {
        return entriesFor("chargePowerInKW", all).stream()
                .filter(e -> !e.timestamp().isBefore(start) && !e.timestamp().isAfter(end))
                .mapToDouble(e -> parseDouble(e.value()))
                .filter(v -> v > 0)
                .max()
                .stream().boxed().findFirst().orElse(null);
    }

    private Integer nearestIntBefore(String field, OffsetDateTime ref, List<DataEntry> all) {
        return entriesFor(field, all).stream()
                .filter(e -> !e.timestamp().isAfter(ref))
                .reduce((a, b) -> b)
                .map(e -> (int) Math.round(parseDouble(e.value())))
                .orElse(null);
    }

    private Integer nearestIntAfter(String field, OffsetDateTime ref, List<DataEntry> all) {
        return entriesFor(field, all).stream()
                .filter(e -> !e.timestamp().isBefore(ref))
                .findFirst()
                .map(e -> (int) Math.round(parseDouble(e.value())))
                .orElse(null);
    }

    private Integer nearestOdometer(OffsetDateTime start, OffsetDateTime end, List<DataEntry> all) {
        // Prefer a reading within or just before/after the session
        return entriesFor("mileage", all).stream()
                .min(Comparator.comparingLong(e -> {
                    long diffToStart = Math.abs(java.time.Duration.between(e.timestamp(), start).toSeconds());
                    long diffToEnd = Math.abs(java.time.Duration.between(e.timestamp(), end).toSeconds());
                    return Math.min(diffToStart, diffToEnd);
                }))
                .map(e -> (int) Math.round(parseDouble(e.value())))
                .orElse(null);
    }

    private Double nearestTemperature(OffsetDateTime start, OffsetDateTime end, List<DataEntry> all) {
        // temperatureOutsideVehicle is in Kelvin
        OffsetDateTime mid = start.plusSeconds(java.time.Duration.between(start, end).toSeconds() / 2);
        return entriesFor("temperatureOutsideVehicle", all).stream()
                .min(Comparator.comparingLong(e ->
                        Math.abs(java.time.Duration.between(e.timestamp(), mid).toSeconds())))
                .map(e -> parseDouble(e.value()) - KELVIN_OFFSET)
                .orElse(null);
    }

    private List<DataEntry> entriesFor(String field, List<DataEntry> all) {
        return all.stream()
                .filter(e -> field.equals(e.field()))
                .sorted(Comparator.comparing(DataEntry::timestamp))
                .collect(Collectors.toList());
    }

    // --- Timestamp parsing ---

    private OffsetDateTime parseTimestamp(String raw) {
        if (raw == null || raw.isBlank() || "N/A".equals(raw)) return null;
        try {
            String s = raw.trim().replace(" ", "T");
            s = truncateToMillis(s);
            if (s.endsWith("Z")) s = s.substring(0, s.length() - 1) + "+00:00";
            // Add UTC offset if none present
            if (!s.contains("+") && !s.matches(".*T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?-\\d{2}:\\d{2}$")) {
                s += "+00:00";
            }
            return OffsetDateTime.parse(s);
        } catch (Exception ignored) {
            return null;
        }
    }

    // Truncates sub-millisecond precision: "13:23:02.984294250" → "13:23:02.984"
    private String truncateToMillis(String s) {
        int dotIdx = s.indexOf('.');
        if (dotIdx < 0) return s;
        int end = dotIdx + 1;
        while (end < s.length() && Character.isDigit(s.charAt(end))) end++;
        if (end - dotIdx - 1 > 3) return s.substring(0, dotIdx + 4) + s.substring(end);
        return s;
    }

    private double parseDouble(String value) {
        try { return Double.parseDouble(value); }
        catch (Exception e) { return 0.0; }
    }

    // Internal DTO - only used within this parser
    private record DataEntry(String field, String value, OffsetDateTime timestamp) {}
}
