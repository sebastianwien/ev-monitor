package com.evmonitor.domain;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Ein Tacho-Abschnitt der CO₂-Bilanz: die Strecke zwischen zwei Tachoständen gegen den Netzstrom
 * der Ladungen dazwischen. Zählt im Zeitraum der schließenden Ladung.
 *
 * @param closingLogId  Ladung mit dem Tachostand, der den Abschnitt schließt
 * @param closedAt      Zeitpunkt dieser Ladung
 * @param distanceKm    Tachodifferenz
 * @param gridKwh       Netz-kWh aller Ladungen des Abschnitts
 * @param petrolLiters  Liter, die ein Benziner der Fahrzeugklasse dafür getankt hätte
 * @param petrolKg      CO₂e dieses Benziners
 * @param electricityKg CO₂e des Netzstroms
 */
public record Co2Segment(
        UUID closingLogId,
        LocalDateTime closedAt,
        int distanceKm,
        double gridKwh,
        double petrolLiters,
        double petrolKg,
        double electricityKg) {

    /** Weniger CO₂e als der Benziner; negativ, wenn der Strom mehr verursacht hat. */
    public double savedKg() {
        return petrolKg - electricityKg;
    }
}
