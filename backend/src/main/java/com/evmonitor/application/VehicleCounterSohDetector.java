package com.evmonitor.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Nutzbare Kapazität aus den Fahrt-Energiezählern des Fahrzeugs, nach denselben Regeln wie die
 * Ladungsquelle {@link BatterySohAutoDetector}: je Fahrt eine Schätzung {@code 100 × Zähler-kWh / SoC-Delta},
 * nur Fahrten mit großem Hub am Stück, daraus der hub-gewichtete Median der letzten 5. Je Fahrt liegen
 * beide Enden in derselben Betriebsphase (Zündung an), Standverbrauch ohne Zählerlauf verfälscht nichts.
 *
 * <p>Warum nur große Hübe: Der SoC kommt in ganzen Prozent. Eine Kurzfahrt mit einem halben Prozent
 * Verbrauch zeigt mal 0, mal 1 Prozentpunkt. Kurzfahrten tragen damit nur Rundungsrauschen, und
 * jede Filterung auf "SoC ist gesunken" macht daraus eine systematisch zu kleine Kapazität. Ab 30 %
 * Hub liegt der Rundungsfehler je Fahrt unter ±3,3 %, der Median über 5 dämpft ihn weiter.
 * Fahrten, die binnen einer Stunde nach einer DC-Ladung starten, zählen nicht: der SoC setzt sich
 * nach dem Schnellladen noch.
 */
public final class VehicleCounterSohDetector {

    static final BigDecimal MIN_TRIP_HUB_PERCENT = new BigDecimal("30");
    static final int WINDOW_SIZE = 5;
    static final Duration SETTLE_AFTER_DC = Duration.ofHours(1);
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private VehicleCounterSohDetector() {}

    /** Eine Fahrt mit Zählerwert; SoC als ganze Prozent, Energie batterieseitig in kWh. */
    public record CounterTrip(Instant startedAt, BigDecimal socStart, BigDecimal socEnd, BigDecimal counterKwh) {}

    /**
     * @param capacityKwh   nutzbare Kapazität, hub-gewichteter Median der Fahrten im Fenster
     * @param tripCount     Fahrten im Fenster
     * @param socHubPercent Hub der Fahrt, die den Median trägt
     */
    public record Detection(BigDecimal capacityKwh, int tripCount, BigDecimal socHubPercent) {}

    /**
     * @param trips        Fahrten mit Zählerwert eines Autos, beliebige Reihenfolge
     * @param dcChargeEnds Enden der DC-Ladungen des Autos; Fahrten kurz danach werden ausgeschlossen
     * @return leer, solange weniger als 5 Fahrten mit mindestens 30 % Hub vorliegen
     */
    public static Optional<Detection> detect(List<CounterTrip> trips, List<Instant> dcChargeEnds) {
        List<CounterTrip> qualifying = trips.stream()
                .filter(t -> qualifies(t, dcChargeEnds))
                .sorted(Comparator.comparing(CounterTrip::startedAt))
                .toList();
        if (qualifying.size() < WINDOW_SIZE) return Optional.empty();

        List<SohWeightedMedian.Estimate> window = qualifying.subList(qualifying.size() - WINDOW_SIZE, qualifying.size())
                .stream()
                .map(t -> new SohWeightedMedian.Estimate(t.counterKwh().multiply(HUNDRED).divide(hub(t), 4, RoundingMode.HALF_UP), hub(t)))
                .toList();
        SohWeightedMedian.Estimate median = SohWeightedMedian.of(window);
        return Optional.of(new Detection(median.capacityKwh().setScale(2, RoundingMode.HALF_UP), WINDOW_SIZE,
                median.socHub().setScale(0, RoundingMode.HALF_UP)));
    }

    private static boolean qualifies(CounterTrip t, List<Instant> dcChargeEnds) {
        if (t.startedAt() == null || t.socStart() == null || t.socEnd() == null || t.counterKwh() == null) return false;
        if (t.counterKwh().signum() <= 0 || hub(t).compareTo(MIN_TRIP_HUB_PERCENT) < 0) return false;
        return !settlingAfterDc(t.startedAt(), dcChargeEnds);
    }

    private static BigDecimal hub(CounterTrip t) {
        return t.socStart().subtract(t.socEnd());
    }

    private static boolean settlingAfterDc(Instant start, List<Instant> dcChargeEnds) {
        for (Instant end : dcChargeEnds) {
            if (!start.isBefore(end) && start.isBefore(end.plus(SETTLE_AFTER_DC))) return true;
        }
        return false;
    }
}
