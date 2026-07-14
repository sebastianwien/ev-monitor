package com.evmonitor.application.imports.eudataact;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Export-Variante ohne sprechende Feldnamen (MEB, z.B. ID.3): die Zeitreihen tragen nur
 * rohe Signal-IDs ({@code 180886}), und es gibt weder Ladezustand noch Ladeleistung.
 * <p>
 * Die IDs sind nicht dokumentiert und werden deshalb nicht hart verdrahtet, sondern pro Datei
 * ueber ihren <b>Wertverlauf</b> identifiziert: Der Ladezustand liegt zwischen 0 und 100 und
 * meldet in 0,5er-Schritten (also halbzahlige Werte), der Kilometerstand ist ganzzahlig und
 * steigt monoton. Passt mehr als eine Zeitreihe oder keine, greift der Detektor nicht -
 * lieber keine Daten als geratene.
 * <p>
 * Die benannten Snapshot-Werte des Exports (etwa {@code hvsoc_info.value}) taugen dafuer nicht:
 * sie werden zum Exportzeitpunkt gezogen, die Zeitreihen enden aber teils Tage vorher, und der
 * SoC driftet in der Zwischenzeit durch Standverlust. Auch ihre Feldnamen unterscheiden sich
 * zwischen Fahrzeugen.
 * <p>
 * Ladevorgaenge werden aus dem SoC-Anstieg rekonstruiert. Getrennt wird primaer ueber den
 * Kilometerstand: aendert er sich, ist das Auto gefahren - dann sind es zwei Ladungen.
 */
class SocCurveSessionDetector implements SessionDetector {

    /** CAN-Marker fuer "Signal nicht verfuegbar" - taucht in echten Exports im Kilometerstand auf. */
    private static final Set<Double> INVALID_MARKERS = Set.of(1048574.0, 1048575.0);

    /** Kuerzere Reihen sind kein belastbarer Verlauf. */
    private static final int MIN_READINGS = 50;
    /** SoC meldet in 0,5er-Schritten; Enums im selben Wertebereich sind rein ganzzahlig. */
    private static final double MIN_HALF_STEP_SHARE = 0.2;
    /** Ein Kilometerstand muss ueber die Exportdauer nennenswert zulegen. */
    private static final double MIN_ODOMETER_SPAN_KM = 100.0;

    /** Groessere Meldeluecke beendet die Ladung (das Fahrzeug meldet sonst alle 2-8 Minuten). */
    private static final Duration MAX_GAP = Duration.ofMinutes(45);
    /** Darunter ist es Messrauschen oder Rekuperation, kein Ladevorgang. */
    private static final double MIN_RISE_PCT = 2.0;

    @Override
    public boolean supports(EntryIndex index) {
        return socSignal(index).isPresent() && odometerSignal(index).isPresent();
    }

    @Override
    public List<EUDataActSession> detect(EntryIndex index) {
        Optional<List<DataEntry>> soc = socSignal(index);
        Optional<List<DataEntry>> odometer = odometerSignal(index);
        if (soc.isEmpty() || odometer.isEmpty()) return List.of();

        List<DataEntry> socReadings = soc.get();
        List<DataEntry> odometerReadings = odometer.get();

        List<EUDataActSession> sessions = new ArrayList<>();
        DataEntry start = null;
        DataEntry end = null;

        for (int i = 1; i < socReadings.size(); i++) {
            DataEntry prev = socReadings.get(i - 1);
            DataEntry curr = socReadings.get(i);

            if (isCharging(prev, curr, odometerReadings)) {
                if (start == null) start = prev;
                end = curr;
            } else if (start != null) {
                addIfSession(sessions, start, end, odometerReadings);
                start = null;
            }
        }
        addIfSession(sessions, start, end, odometerReadings);

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
                null)); // keine Aussentemperatur
    }

    private Integer odometerAt(List<DataEntry> odometer, OffsetDateTime t) {
        Integer value = null;
        for (DataEntry e : odometer) {
            if (e.timestamp().isAfter(t)) break;
            value = (int) Math.round(e.asDouble());
        }
        return value;
    }

    // --- Signal-Identifikation ueber den Wertverlauf ---

    /**
     * Der Ladezustand: Werte zwischen 0 und 100, ein nennenswerter Teil davon halbzahlig.
     * Das trennt ihn von den Enums des Thermomanagements, die im selben Wertebereich liegen,
     * aber rein ganzzahlig sind.
     */
    private Optional<List<DataEntry>> socSignal(EntryIndex index) {
        return uniqueSignal(index, readings -> {
            List<Double> values = values(readings);
            if (values.stream().anyMatch(v -> v < 0.0 || v > 100.0)) return false;
            long halfSteps = values.stream().filter(v -> v != Math.floor(v)).count();
            return (double) halfSteps / values.size() >= MIN_HALF_STEP_SHARE;
        }).map(this::plausibleSocReadings);
    }

    /** Der Kilometerstand: ganzzahlig, monoton steigend, ueber die Exportdauer nennenswert gewachsen. */
    private Optional<List<DataEntry>> odometerSignal(EntryIndex index) {
        return uniqueSignal(index, readings -> {
            List<Double> values = values(readings);
            if (values.stream().anyMatch(v -> v != Math.floor(v))) return false;
            for (int i = 1; i < readings.size(); i++) {
                // Mehrere Messwerte teilen sich denselben Zeitstempel - untereinander sagt ihre
                // Reihenfolge nichts aus, ein Rueckschritt darin ist keine Monotonie-Verletzung.
                boolean sameInstant = readings.get(i).timestamp().isEqual(readings.get(i - 1).timestamp());
                if (!sameInstant && values.get(i) < values.get(i - 1)) return false;
            }
            return values.get(values.size() - 1) - values.get(0) > MIN_ODOMETER_SPAN_KM;
        });
    }

    /**
     * Genau eine Zeitreihe darf passen. Passen mehrere, waere die Zuordnung geraten -
     * dann greift der Detektor lieber gar nicht.
     */
    private Optional<List<DataEntry>> uniqueSignal(EntryIndex index, Predicate<List<DataEntry>> matches) {
        List<List<DataEntry>> candidates = index.fields().stream()
                .filter(SocCurveSessionDetector::isSignalId)
                .map(field -> usableReadings(index.get(field)))
                .filter(readings -> readings.size() >= MIN_READINGS)
                .filter(matches)
                .limit(2)
                .toList();

        return candidates.size() == 1 ? Optional.of(candidates.get(0)) : Optional.empty();
    }

    /** Nicht-numerische Werte ("Init") und Ungueltig-Marker sind keine Messwerte. */
    private List<DataEntry> usableReadings(List<DataEntry> readings) {
        return readings.stream()
                .filter(DataEntry::isNumeric)
                .filter(e -> !INVALID_MARKERS.contains(e.asDouble()))
                .toList();
    }

    /** SoC=0.0 sind Offline-Platzhalter mitten in der Kurve, keine echten Messwerte. */
    private List<DataEntry> plausibleSocReadings(List<DataEntry> readings) {
        return readings.stream()
                .filter(e -> e.asDouble() > 0.0)
                .toList();
    }

    private List<Double> values(List<DataEntry> readings) {
        return readings.stream().map(DataEntry::asDouble).toList();
    }

    private static boolean isSignalId(String field) {
        return !field.isEmpty() && field.chars().allMatch(Character::isDigit);
    }
}
