package com.evmonitor.application.imports.eudataact;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Export-Variante mit sprechenden Feldnamen (z.B. ID.7): das Fahrzeug meldet
 * {@code chargingState} und {@code chargePowerInKW} direkt. Die bevorzugte Variante -
 * echte Ladeleistung, echter Ladetyp, kWh per Integration.
 */
class ChargingStateSessionDetector implements SessionDetector {

    private static final String CHARGING = "CHARGING";
    private static final double KELVIN_OFFSET = 273.15;

    private static final String F_STATE = "chargingState";
    private static final String F_POWER = "chargePowerInKW";
    private static final String F_SOC = "currentSOCInPct";
    private static final String F_MILEAGE = "mileage";
    private static final String F_PLUG = "plugConnectionState";
    private static final String F_TYPE = "chargeType";
    private static final String F_TEMP = "temperatureOutsideVehicle";

    @Override
    public boolean supports(EntryIndex index) {
        return index.has(F_STATE);
    }

    @Override
    public List<EUDataActSession> detect(EntryIndex index) {
        List<EUDataActSession> sessions = new ArrayList<>();
        boolean inCharging = false;
        OffsetDateTime sessionStart = null;
        OffsetDateTime lastChargingTs = null;

        for (DataEntry entry : index.get(F_STATE)) {
            boolean isCharging = CHARGING.equals(entry.value());

            if (isCharging && !inCharging) {
                sessionStart = entry.timestamp();
                inCharging = true;
            } else if (!isCharging && inCharging) {
                sessions.add(buildSession(sessionStart, lastChargingTs, index));
                inCharging = false;
            }

            if (isCharging) {
                lastChargingTs = entry.timestamp();
            }
        }

        // Datei endet mitten im Ladevorgang
        if (inCharging && sessionStart != null) {
            sessions.add(buildSession(sessionStart, lastChargingTs, index));
        }

        return sessions.stream()
                .sorted(Comparator.comparing(EUDataActSession::startedAt))
                .toList();
    }

    private EUDataActSession buildSession(OffsetDateTime start, OffsetDateTime end, EntryIndex index) {
        int durationMin = (int) Duration.between(start, end).toMinutes();

        return new EUDataActSession(
                start,
                end,
                durationMin,
                nearestIntBefore(F_SOC, start, index),
                nearestIntAfter(F_SOC, end, index),
                null, // socDeltaPct: nicht noetig, kWh kommt aus der Integration
                dominantChargeType(start, end, index),
                maxPowerKw(start, end, index),
                integrateKwh(start, end, index),
                nearestOdometer(start, end, index),
                nearestTemperature(start, end, index));
    }

    // --- kWh-Integration (Trapez, mit Lueckenerkennung) ---

    private Double integrateKwh(OffsetDateTime start, OffsetDateTime end, EntryIndex index) {
        List<DataEntry> powerReadings = index.between(F_POWER, start, end);
        if (powerReadings.size() < 2) return null;

        double kwh = 0.0;
        for (int i = 1; i < powerReadings.size(); i++) {
            DataEntry prev = powerReadings.get(i - 1);
            DataEntry curr = powerReadings.get(i);

            if (hasStopSignal(prev.timestamp(), curr.timestamp(), index)) continue;

            double p1 = Math.max(0.0, prev.asDouble());
            double p2 = Math.max(0.0, curr.asDouble());
            double deltaHours = Duration.between(prev.timestamp(), curr.timestamp()).toSeconds() / 3600.0;

            kwh += (p1 + p2) / 2.0 * deltaHours;
        }

        return kwh > 0 ? kwh : null;
    }

    /** In der Luecke wurde die Ladung beendet, der Stecker gezogen oder das Auto bewegt. */
    private boolean hasStopSignal(OffsetDateTime from, OffsetDateTime to, EntryIndex index) {
        boolean stateStopped = index.between(F_STATE, from, to).stream()
                .anyMatch(e -> !CHARGING.equals(e.value()));
        boolean unplugged = index.between(F_PLUG, from, to).stream()
                .anyMatch(e -> "disconnected".equals(e.value()));
        return stateStopped || unplugged || mileageChanged(from, to, index);
    }

    private boolean mileageChanged(OffsetDateTime from, OffsetDateTime to, EntryIndex index) {
        Optional<DataEntry> before = index.lastAt(F_MILEAGE, from);
        if (before.isEmpty()) return false;
        return index.between(F_MILEAGE, from, to).stream()
                .anyMatch(e -> !e.value().equals(before.get().value()));
    }

    // --- Feld-Helfer ---

    private String dominantChargeType(OffsetDateTime start, OffsetDateTime end, EntryIndex index) {
        Map<String, Long> counts = index.between(F_TYPE, start, end).stream()
                .filter(e -> !"INVALID".equals(e.value()))
                .collect(Collectors.groupingBy(DataEntry::value, Collectors.counting()));
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private Double maxPowerKw(OffsetDateTime start, OffsetDateTime end, EntryIndex index) {
        return index.between(F_POWER, start, end).stream()
                .mapToDouble(DataEntry::asDouble)
                .filter(v -> v > 0)
                .max()
                .stream().boxed().findFirst().orElse(null);
    }

    private Integer nearestIntBefore(String field, OffsetDateTime ref, EntryIndex index) {
        return index.lastAt(field, ref)
                .map(e -> (int) Math.round(e.asDouble()))
                .orElse(null);
    }

    private Integer nearestIntAfter(String field, OffsetDateTime ref, EntryIndex index) {
        return index.get(field).stream()
                .filter(e -> !e.timestamp().isBefore(ref))
                .findFirst()
                .map(e -> (int) Math.round(e.asDouble()))
                .orElse(null);
    }

    private Integer nearestOdometer(OffsetDateTime start, OffsetDateTime end, EntryIndex index) {
        return index.get(F_MILEAGE).stream()
                .min(Comparator.comparingLong(e -> Math.min(
                        Math.abs(Duration.between(e.timestamp(), start).toSeconds()),
                        Math.abs(Duration.between(e.timestamp(), end).toSeconds()))))
                .map(e -> (int) Math.round(e.asDouble()))
                .orElse(null);
    }

    private Double nearestTemperature(OffsetDateTime start, OffsetDateTime end, EntryIndex index) {
        // temperatureOutsideVehicle kommt in Kelvin
        OffsetDateTime mid = start.plusSeconds(Duration.between(start, end).toSeconds() / 2);
        return index.get(F_TEMP).stream()
                .min(Comparator.comparingLong(e ->
                        Math.abs(Duration.between(e.timestamp(), mid).toSeconds())))
                .map(e -> e.asDouble() - KELVIN_OFFSET)
                .orElse(null);
    }
}
