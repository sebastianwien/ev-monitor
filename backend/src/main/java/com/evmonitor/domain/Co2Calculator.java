package com.evmonitor.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Einzige Formel für gespartes CO₂ gegen einen Benziner der Fahrzeugklasse (Statistik, Story,
 * Rückblick, Meilenstein, Community-Ticker).
 *
 * Gerechnet wird je Tacho-Abschnitt: alle Ladungen nach einem Tachostand bis einschließlich der
 * Ladung, deren Tachostand höher ist. km = Tachodifferenz, Strom = Netz-kWh dieser Ladungen mal
 * Strommix ihres Jahres. So stehen km und kWh, die zusammengehören, auch dann gegeneinander, wenn
 * dazwischen Ladungen ohne Tachostand oder Teilladungen am gleichen Tachostand liegen.
 *
 * Beidseitig weggelassen: Ladungen bis zum ersten Tachostand und nach dem letzten, Abschnitte mit
 * Tacho rückwärts, mit einer Ladung ohne Energie oder mit Netz-kWh je 100 km außerhalb der Grenzen
 * (fehlende Ladungen, Tachofehler). Kein Abschneiden bei 0, die Summen bleiben vorzeichenrichtig.
 */
public final class Co2Calculator {

    private final double minGridKwhPer100km;
    private final double maxGridKwhPer100km;

    /** Grenzen für Netz-kWh je 100 km eines Abschnitts. */
    public Co2Calculator(double minGridKwhPer100km, double maxGridKwhPer100km) {
        this.minGridKwhPer100km = minGridKwhPer100km;
        this.maxGridKwhPer100km = maxGridKwhPer100km;
    }

    /**
     * Abschnitte eines Autos in zeitlicher Reihenfolge.
     *
     * @param logs     Ladungen des Autos, Reihenfolge egal; Ladungen außerhalb der Auswertung zählen nicht
     * @param category Fahrzeugklasse für die Liter des Benziners
     * @param gridKwh  Netz-kWh einer Ladung, null wenn unbekannt
     */
    public List<Co2Segment> segments(List<EvLog> logs, VehicleCategory category, Function<EvLog, BigDecimal> gridKwh) {
        List<EvLog> sorted = logs.stream()
                .filter(EvLog::isIncludeInStatistics)
                .sorted(Comparator.comparing(EvLog::getLoggedAt))
                .toList();
        double litersPer100km = category.getPetrolLitersPer100Km().doubleValue();

        List<Co2Segment> result = new ArrayList<>();
        List<EvLog> pending = new ArrayList<>();
        Integer anchorKm = null;
        for (EvLog log : sorted) {
            Integer odometer = log.getOdometerKm();
            if (anchorKm == null) {
                // Bis zum ersten Tachostand ist die Strecke unbekannt
                if (odometer != null) anchorKm = odometer;
                continue;
            }
            pending.add(log);
            if (odometer == null || odometer.equals(anchorKm)) continue;
            if (odometer > anchorKm) {
                segment(pending, odometer - anchorKm, litersPer100km, gridKwh).ifPresent(result::add);
            }
            pending.clear();
            anchorKm = odometer;
        }
        return result;
    }

    private Optional<Co2Segment> segment(List<EvLog> logs, int distanceKm, double litersPer100km,
                                         Function<EvLog, BigDecimal> gridKwh) {
        double kwh = 0;
        double electricityGrams = 0;
        for (EvLog log : logs) {
            BigDecimal k = gridKwh.apply(log);
            if (k == null || k.signum() <= 0) return Optional.empty();
            kwh += k.doubleValue();
            electricityGrams += k.doubleValue() * Co2Factors.gridGramsPerKwh(log.getLoggedAt().getYear());
        }
        double kwhPer100km = kwh / distanceKm * 100.0;
        if (kwhPer100km < minGridKwhPer100km || kwhPer100km > maxGridKwhPer100km) return Optional.empty();

        double liters = distanceKm * litersPer100km / 100.0;
        EvLog closing = logs.get(logs.size() - 1);
        return Optional.of(new Co2Segment(closing.getId(), closing.getLoggedAt(), distanceKm, kwh,
                liters, liters * Co2Factors.PETROL_KG_CO2E_PER_LITER, electricityGrams / 1000.0));
    }
}
