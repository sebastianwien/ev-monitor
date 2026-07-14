package com.evmonitor.application.imports.eudataact;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Export-Variante ohne sprechende Feldnamen (MEB, z.B. ID.3): die Zeitreihen tragen nur
 * rohe Signal-IDs ({@code 180886}), und es gibt weder Ladezustand noch Ladeleistung.
 * <p>
 * Die IDs sind nicht dokumentiert und werden deshalb nicht hart verdrahtet, sondern pro Datei
 * ueber die mitgelieferten Einzelwerte aufgeloest: {@code hvsoc_info.value} verraet SoC und
 * Zeitpunkt, damit laesst sich genau die Zeitreihe identifizieren, die dort denselben Wert hat.
 * Loest ein Anker nicht eindeutig auf, greift der Detektor nicht (statt falsche Daten zu liefern).
 * <p>
 * Ladevorgaenge werden aus dem SoC-Anstieg rekonstruiert. Getrennt wird primaer ueber den
 * Kilometerstand: aendert er sich, ist das Auto gefahren - dann sind es zwei Ladungen.
 */
class SocCurveSessionDetector implements SessionDetector {

    private static final String ANCHOR_SOC = "hvsoc_info.value";
    private static final String ANCHOR_ODOMETER = "mileage_info.value";

    /** SoC-Aufloesung ist 0,5 % - der Anker darf um maximal einen Schritt abweichen. */
    private static final double ANCHOR_TOLERANCE = 0.51;

    /** Groessere Meldeluecke beendet die Ladung (das Fahrzeug meldet sonst alle 2-8 Minuten). */
    private static final Duration MAX_GAP = Duration.ofMinutes(45);
    /** Darunter ist es Messrauschen oder Rekuperation, kein Ladevorgang. */
    private static final double MIN_RISE_PCT = 2.0;

    @Override
    public boolean supports(EntryIndex index) {
        return resolveSignal(ANCHOR_SOC, index).isPresent()
                && resolveSignal(ANCHOR_ODOMETER, index).isPresent();
    }

    @Override
    public List<EUDataActSession> detect(EntryIndex index) {
        Optional<String> socSignal = resolveSignal(ANCHOR_SOC, index);
        Optional<String> odometerSignal = resolveSignal(ANCHOR_ODOMETER, index);
        if (socSignal.isEmpty() || odometerSignal.isEmpty()) return List.of();

        List<DataEntry> soc = plausibleSocReadings(index.get(socSignal.get()));
        List<DataEntry> odometer = index.get(odometerSignal.get());

        List<EUDataActSession> sessions = new ArrayList<>();
        DataEntry start = null;
        DataEntry end = null;

        for (int i = 1; i < soc.size(); i++) {
            DataEntry prev = soc.get(i - 1);
            DataEntry curr = soc.get(i);

            if (isCharging(prev, curr, odometer)) {
                if (start == null) start = prev;
                end = curr;
            } else if (start != null) {
                addIfSession(sessions, start, end, odometer);
                start = null;
            }
        }
        addIfSession(sessions, start, end, odometer);

        return sessions;
    }

    private boolean isCharging(DataEntry prev, DataEntry curr, List<DataEntry> odometer) {
        boolean rising = curr.asDouble() > prev.asDouble();
        boolean withinGap = Duration.between(prev.timestamp(), curr.timestamp()).compareTo(MAX_GAP) <= 0;
        boolean drove = !Objects.equals(
                odometerAt(odometer, prev.timestamp()),
                odometerAt(odometer, curr.timestamp()));
        return rising && withinGap && !drove;
    }

    private void addIfSession(List<EUDataActSession> sessions, DataEntry start, DataEntry end,
                              List<DataEntry> odometer) {
        if (start == null || end == null) return;

        double delta = end.asDouble() - start.asDouble();
        if (delta < MIN_RISE_PCT) return;

        sessions.add(new EUDataActSession(
                start.timestamp(),
                end.timestamp(),
                (int) Duration.between(start.timestamp(), end.timestamp()).toMinutes(),
                (int) Math.round(start.asDouble()),
                (int) Math.round(end.asDouble()),
                delta,
                null, // kein Ladetyp-Signal in dieser Variante - nicht raten
                null, // keine Ladeleistung
                null, // kWh braucht die Batteriekapazitaet, die kennt erst der Service
                odometerAt(odometer, start.timestamp()),
                null)); // keine Aussentemperatur (Anker loest nicht auf)
    }

    /** SoC=0.0 sind Offline-Platzhalter mitten in der Kurve, keine echten Messwerte. */
    private List<DataEntry> plausibleSocReadings(List<DataEntry> readings) {
        return readings.stream()
                .filter(DataEntry::isNumeric)
                .filter(e -> e.asDouble() > 0.0 && e.asDouble() <= 100.0)
                .toList();
    }

    private Integer odometerAt(List<DataEntry> odometer, OffsetDateTime t) {
        Integer value = null;
        for (DataEntry e : odometer) {
            if (e.timestamp().isAfter(t)) break;
            value = (int) Math.round(e.asDouble());
        }
        return value;
    }

    /**
     * Sucht die Signal-ID, deren Zeitreihe zum Ankerzeitpunkt den Ankerwert traegt.
     * Nur eindeutige Treffer zaehlen - passen mehrere Signale, waere die Zuordnung geraten.
     */
    private Optional<String> resolveSignal(String anchorField, EntryIndex index) {
        Optional<DataEntry> anchor = index.get(anchorField).stream()
                .filter(DataEntry::isNumeric)
                .findFirst();
        if (anchor.isEmpty()) return Optional.empty();

        OffsetDateTime at = anchor.get().timestamp();
        double expected = anchor.get().asDouble();

        List<String> matches = index.fields().stream()
                .filter(SocCurveSessionDetector::isSignalId)
                // Kein Zeitfenster: der letzte gemeldete Wert eines Signals ist sein Wert.
                // Der Kilometerstand etwa meldet nur beim Fahren - steht das Auto zum Export
                // seit Stunden, waere jede Fensterbegrenzung ein Fehlschlag.
                .filter(field -> index.lastAt(field, at)
                        .filter(DataEntry::isNumeric)
                        .filter(e -> Math.abs(e.asDouble() - expected) <= ANCHOR_TOLERANCE)
                        .isPresent())
                .limit(2)
                .toList();

        return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
    }

    private static boolean isSignalId(String field) {
        return !field.isEmpty() && field.chars().allMatch(Character::isDigit);
    }
}
