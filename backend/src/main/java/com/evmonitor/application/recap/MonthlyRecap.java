package com.evmonitor.application.recap;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Zahlen eines Monats für die Rückblick-Mail. Optionale Werte sind null, wenn die Daten sie
 * nicht hergeben; die Mail lässt die zugehörige Zeile dann weg, statt eine Null zu zeigen.
 *
 * @param costEur                Stromkosten, null wenn bei keiner Ladung ein Preis eingetragen ist
 * @param distanceKm             gefahrene Strecke aus dem Kilometerstand, null ohne Odometer-Daten
 * @param consumptionKwhPer100km Verbrauch aus der Statistik (dieselbe Rechnung wie im Dashboard)
 * @param homeSharePercent       Anteil der daheim geladenen kWh, null wenn nichts privat geladen wurde
 * @param fuelCostEur            was ein Verbrenner für dieselbe Strecke getankt hätte, null ohne Strecke
 * @param pricelessHint          der eine Datenqualitätshinweis der Mail, null wenn alle Preise da sind
 * @param previousDistanceKm     Strecke des Vormonats aus derselben Statistik, null ohne Daten
 */
public record MonthlyRecap(
        UUID userId,
        UUID carId,
        String email,
        String username,
        String locale,
        YearMonth month,
        String carName,
        int charges,
        int acCharges,
        int dcCharges,
        BigDecimal kwh,
        BigDecimal costEur,
        BigDecimal distanceKm,
        BigDecimal consumptionKwhPer100km,
        Integer homeSharePercent,
        BigDecimal fuelCostEur,
        BigDecimal fuelPricePerLiter,
        PricelessHint pricelessHint,
        BigDecimal previousDistanceKm
) {

    /** Ladungen ohne Preis. firstAt und firstKwh beschreiben die früheste, für den Fall count == 1. */
    public record PricelessHint(int count, LocalDateTime firstAt, BigDecimal firstKwh) {}

    /** Was Strom gegenüber dem Benziner gespart hat. null, wenn nichts zu vergleichen ist oder Strom teurer war. */
    public BigDecimal savingsEur() {
        if (costEur == null || fuelCostEur == null) {
            return null;
        }
        BigDecimal savings = fuelCostEur.subtract(costEur);
        return savings.signum() > 0 ? savings : null;
    }

    public BigDecimal costPer100Km() {
        if (costEur == null || distanceKm == null || distanceKm.signum() <= 0) {
            return null;
        }
        return costEur.multiply(BigDecimal.valueOf(100)).divide(distanceKm, 2, RoundingMode.HALF_UP);
    }
}
