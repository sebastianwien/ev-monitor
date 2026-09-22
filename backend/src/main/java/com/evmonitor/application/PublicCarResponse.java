package com.evmonitor.application;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Eine oeffentlich geteilte Fahrzeugseite - was ein nicht angemeldeter Besucher
 * ueber ein fremdes Auto sieht.
 *
 * Das Record ist absichtlich schmal: was hier kein Feld hat, kann auch nicht
 * versehentlich oeffentlich werden. Nicht enthalten sind Kennzeichen, Kilometerstand,
 * Geohash, Betreiber, Uhrzeiten und jeder Bezug auf den Besitzer.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PublicCarResponse(
        /** Anzeigename aus dem Modell-Enum, z.B. "Tesla Model 3". */
        String carModel,
        Integer year,
        /** true wenn der Besitzer sein Foto oeffentlich gestellt hat - dann liegt es unter /image. */
        boolean hasImage,
        /** Pfad der oeffentlichen Modell-Seite, z.B. "/modelle/Tesla/Model_3". */
        String modelPagePath,
        Integer totalCharges,
        BigDecimal totalKwhCharged,
        BigDecimal totalDistanceKm,
        BigDecimal avgConsumptionKwhPer100km,
        BigDecimal avgCostPerKwh,
        /** Ladekosten je 100 km, null ohne Distanz oder Kosten. */
        BigDecimal costPer100km,
        /** Anteil oeffentlich geladener kWh in Prozent, null ohne Zuordnung. */
        BigDecimal publicChargingSharePercent,
        BigDecimal summerConsumptionKwhPer100km,
        BigDecimal winterConsumptionKwhPer100km,
        /** Vergleich zum Community-Schnitt desselben Modells, null ohne Peers. */
        PeerComparison peerComparison,
        /** Letzte zwoelf Monate mit Daten, aelteste zuerst. */
        List<MonthPoint> months,
        /** Neueste Ladungen zuerst. */
        List<Charge> recentCharges) {

    public record PeerComparison(
            BigDecimal peerAvgConsumptionKwhPer100km,
            /** Anzahl anderer Fahrer hinter dem Schnitt - nie wer. */
            int peerUsers,
            /** SPEC = gleiche Variante, MODEL = alle Varianten des Modells. */
            String matchType) {}

    public record MonthPoint(
            /** Erster Tag des Monats. */
            LocalDate month,
            BigDecimal kwhCharged,
            BigDecimal costEur,
            BigDecimal consumptionKwhPer100km) {}

    public record Charge(
            LocalDate chargedOn,
            BigDecimal kwhCharged,
            BigDecimal costEur,
            Integer durationMinutes,
            String chargingType,
            BigDecimal maxChargingPowerKw,
            BigDecimal consumptionKwhPer100km,
            Boolean publicCharging) {}
}
