package com.evmonitor.domain;

import java.util.Collection;

/**
 * CO₂-Bilanz eines Zeitraums: Summe seiner Tacho-Abschnitte, vorzeichenrichtig.
 *
 * @param distanceKm    Strecke, auf der gerechnet wurde (nur vollständige Abschnitte)
 * @param petrolLiters  Liter, die ein Benziner der Fahrzeugklasse getankt hätte
 * @param petrolKg      CO₂e dieses Benziners
 * @param electricityKg CO₂e des geladenen Netzstroms
 * @param savedKg       petrolKg minus electricityKg, negativ bei mehr CO₂e als der Benziner
 */
public record Co2Balance(
        int distanceKm,
        double petrolLiters,
        double petrolKg,
        double electricityKg,
        double savedKg) {

    /** Summe der Abschnitte; null ohne Abschnitt. */
    public static Co2Balance of(Collection<Co2Segment> segments) {
        if (segments == null || segments.isEmpty()) return null;
        int km = 0;
        double liters = 0;
        double petrol = 0;
        double electricity = 0;
        for (Co2Segment s : segments) {
            km += s.distanceKm();
            liters += s.petrolLiters();
            petrol += s.petrolKg();
            electricity += s.electricityKg();
        }
        return new Co2Balance(km, liters, petrol, electricity, petrol - electricity);
    }
}
