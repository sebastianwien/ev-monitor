package com.evmonitor.application;

import java.time.Instant;

/**
 * Bundesweite Durchschnittspreise je Liter. updatedAt ist null, solange nur die
 * Fallback-Werte vorliegen (kein Tankerkoenig-Key oder noch kein erfolgreicher Abruf).
 * previousMonthPetrolEurPerLitre ist der eingefrorene Benzinpreis des Vormonats für den
 * Story-Rückblick, gleich dem Wert der Monatsmail.
 */
public record FuelPriceResponse(
        double petrolEurPerLitre,
        double dieselEurPerLitre,
        Instant updatedAt,
        double previousMonthPetrolEurPerLitre
) {}
