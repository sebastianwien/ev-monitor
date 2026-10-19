package com.evmonitor.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Nutzbare kWh je angezeigtem SoC-Prozent aus den Fahrt-Energiezählern des Fahrzeugs: Summe der
 * Zähler-kWh aller Fahrten durch Summe ihrer SoC-Deltas. Je Fahrt liegen beide Enden in derselben
 * Betriebsphase (Zündung an), Standverbrauch ohne Zählerlauf verfälscht nichts. Das ist das
 * Entlade-Gegenstück zu Teslas {@code EnergyRemaining / SoC}: zwei Fahrzeugwerte dividiert, keine
 * Ladeverlust-Unsicherheit wie bei CHARGE_LOG.
 *
 * <p>Fehlerbild: SoC in ganzen Prozent, je Fahrt ±1 Prozentpunkt. Ab 300 % Gesamthub bleibt der
 * Rundungsfehler unter ±0,3 %, darunter trägt der Wert die Unsicherheit einzelner Kurzfahrten.
 * Fahrten, die binnen einer Stunde nach einer DC-Ladung starten, zählen nicht: der SoC setzt sich
 * nach dem Schnellladen noch (beobachtet: 5 km ohne SoC-Delta).
 */
public final class VehicleCounterSohDetector {

    static final BigDecimal MIN_TOTAL_HUB_PERCENT = new BigDecimal("300");
    static final int MIN_TRIPS = 20;
    static final Duration SETTLE_AFTER_DC = Duration.ofHours(1);
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private VehicleCounterSohDetector() {}

    /** Eine Fahrt mit Zählerwert; SoC als ganze Prozent, Energie batterieseitig in kWh. */
    public record CounterTrip(Instant startedAt, BigDecimal socStart, BigDecimal socEnd, BigDecimal counterKwh) {}

    /**
     * @param capacityKwh     nutzbare Kapazität: 100 × Summe kWh / Summe SoC-Delta
     * @param tripCount       gezählte Fahrten
     * @param totalHubPercent Summe der SoC-Deltas
     */
    public record Detection(BigDecimal capacityKwh, int tripCount, BigDecimal totalHubPercent) {}

    /**
     * @param trips        Fahrten mit Zählerwert eines Autos, beliebige Reihenfolge
     * @param dcChargeEnds Enden der DC-Ladungen des Autos; Fahrten kurz danach werden ausgeschlossen
     * @return leer, solange Gesamthub oder Anzahl unter den Toren liegen
     */
    public static Optional<Detection> detect(List<CounterTrip> trips, List<Instant> dcChargeEnds) {
        BigDecimal kwh = BigDecimal.ZERO;
        BigDecimal hub = BigDecimal.ZERO;
        int count = 0;
        for (CounterTrip t : trips) {
            if (t.socStart() == null || t.socEnd() == null || t.counterKwh() == null) continue;
            BigDecimal delta = t.socStart().subtract(t.socEnd());
            if (delta.signum() <= 0 || t.counterKwh().signum() <= 0) continue;
            if (t.startedAt() == null || settlingAfterDc(t.startedAt(), dcChargeEnds)) continue;
            kwh = kwh.add(t.counterKwh());
            hub = hub.add(delta);
            count++;
        }
        if (count < MIN_TRIPS || hub.compareTo(MIN_TOTAL_HUB_PERCENT) < 0) return Optional.empty();
        BigDecimal capacity = kwh.multiply(HUNDRED).divide(hub, 2, RoundingMode.HALF_UP);
        return Optional.of(new Detection(capacity, count, hub.setScale(0, RoundingMode.HALF_UP)));
    }

    private static boolean settlingAfterDc(Instant start, List<Instant> dcChargeEnds) {
        for (Instant end : dcChargeEnds) {
            if (!start.isBefore(end) && start.isBefore(end.plus(SETTLE_AFTER_DC))) return true;
        }
        return false;
    }
}
