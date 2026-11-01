package com.evmonitor.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Fahrverbrauch: Energie, die während der Fahrt aus dem Akku geht, mit Heizung und Klima, ohne Stand.
 * Gegenstück zum Verbrauch aus Ladungen ({@code EvLogService}); Basis von Reichweite, WLTP-Vergleich
 * und Peer-Vergleich beim Fahren. Fachliche Regeln siehe docs/features/consumption-calculation.md.
 *
 * <p>Nur Fahrten ab {@link #MIN_TRIP_KM} km zählen: Dort hat sich Heizen amortisiert und die SoC-Rundung
 * wiegt wenig. Eine Fahrt außerhalb von {@link #TRIP_MIN_KWH_PER_100KM} bis {@link #TRIP_MAX_KWH_PER_100KM}
 * ist ein Messfehler und zählt als Fahrt ohne Energiedaten. Gezählt wird nur die Summe, nie der Schnitt
 * einzelner Fahrten.
 *
 * @param kwh          Summe kWh der Fahrten mit plausiblen Energiedaten
 * @param kmWithEnergy Summe km derselben Fahrten
 * @param kmTotal      Summe km aller gezählten Fahrten, auch ohne Energiedaten
 */
public record DriveConsumption(BigDecimal kwh, BigDecimal kmWithEnergy, BigDecimal kmTotal) {

    public static final BigDecimal MIN_TRIP_KM = new BigDecimal("20");
    static final BigDecimal TRIP_MIN_KWH_PER_100KM = new BigDecimal("5");
    static final BigDecimal TRIP_MAX_KWH_PER_100KM = new BigDecimal("50");
    static final BigDecimal MIN_KM = new BigDecimal("100");
    static final BigDecimal MIN_COVERAGE = new BigDecimal("0.80");
    static final BigDecimal RESULT_MIN_KWH_PER_100KM = new BigDecimal("10");
    static final BigDecimal RESULT_MAX_KWH_PER_100KM = new BigDecimal("40");

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    public static final DriveConsumption EMPTY = new DriveConsumption(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

    /** Summen über die Fahrten eines Autos; SoC-Fahrten mit seiner effektiven (SoH-adjustierten) Kapazität. */
    public static DriveConsumption of(List<DriveTripSample> trips, BigDecimal effectiveCapacityKwh) {
        BigDecimal kwh = BigDecimal.ZERO;
        BigDecimal withEnergy = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        for (DriveTripSample t : trips) {
            BigDecimal km = t.distanceKm();
            if (km == null || km.compareTo(MIN_TRIP_KM) < 0) continue;
            total = total.add(km);
            BigDecimal tripKwh = t.consumedKwh(effectiveCapacityKwh);
            if (tripKwh == null) continue;
            BigDecimal per100 = tripKwh.multiply(HUNDRED).divide(km, 4, RoundingMode.HALF_UP);
            if (per100.compareTo(TRIP_MIN_KWH_PER_100KM) < 0 || per100.compareTo(TRIP_MAX_KWH_PER_100KM) > 0) continue;
            kwh = kwh.add(tripKwh);
            withEnergy = withEnergy.add(km);
        }
        return new DriveConsumption(kwh, withEnergy, total);
    }

    public DriveConsumption plus(DriveConsumption other) {
        return new DriveConsumption(kwh.add(other.kwh), kmWithEnergy.add(other.kmWithEnergy), kmTotal.add(other.kmTotal));
    }

    /** Anteil der km mit Energiedaten; 0 ohne Fahrten. */
    public BigDecimal coverage() {
        return kmTotal.signum() == 0 ? BigDecimal.ZERO : kmWithEnergy.divide(kmTotal, 2, RoundingMode.HALF_UP);
    }

    /** Mindestens 100 km mit Energiedaten, 80 % Abdeckung, Ergebnis 10 bis 40 kWh/100 km. */
    public boolean isReliable() {
        if (kmWithEnergy.compareTo(MIN_KM) < 0) return false;
        if (coverage().compareTo(MIN_COVERAGE) < 0) return false;
        BigDecimal raw = raw();
        return raw.compareTo(RESULT_MIN_KWH_PER_100KM) >= 0 && raw.compareTo(RESULT_MAX_KWH_PER_100KM) <= 0;
    }

    /** Fahrverbrauch in kWh/100 km, null wenn die Daten nicht reichen ({@link #isReliable()}). */
    public BigDecimal kwhPer100km() {
        return isReliable() ? raw().setScale(2, RoundingMode.HALF_UP) : null;
    }

    private BigDecimal raw() {
        return kwh.multiply(HUNDRED).divide(kmWithEnergy, 4, RoundingMode.HALF_UP);
    }
}
