package com.evmonitor.application;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Eine oeffentlich geteilte Ladekurve - der einzige Blick, den ein nicht
 * angemeldeter Besucher auf fremde Ladedaten bekommt.
 *
 * Das Record ist absichtlich schmal: was hier kein Feld hat, kann auch nicht
 * versehentlich oeffentlich werden. Nicht enthalten sind Kilometerstand,
 * Geohash, Kosten, Temperatur, Fahrzeug-Trim und jeder Bezug auf den Besitzer.
 *
 * {@code chargedOn} ist bewusst ein Datum ohne Uhrzeit - wann jemand typischerweise
 * laedt, ist ein Bewegungsmuster; fuer die Einordnung der Kurve (Sommer/Winter)
 * reicht der Tag.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PublicCurveResponse(
        List<PowerCurveResponse.Point> points,
        /** Anzeigename aus dem Modell-Enum, z.B. "Tesla Model 3". */
        String carModel,
        BigDecimal kwhCharged,
        Integer durationMinutes,
        BigDecimal socBefore,
        BigDecimal socAfter,
        /** Spitzenleistung aus dem Log, ersatzweise aus der Kurve. */
        BigDecimal peakKw,
        /** Betreiber der Ladesaeule, z.B. "Tesla Supercharger". Nie der Standort. */
        String cpoName,
        String chargingType,
        LocalDate chargedOn) {}
