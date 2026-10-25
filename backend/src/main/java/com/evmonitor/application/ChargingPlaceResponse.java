package com.evmonitor.application;

import com.evmonitor.domain.ChargingType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Ein Ladeort der Story-Karte, so grob wie gespeichert (öffentlich 7, privat 6 Geohash-Zeichen).
 *
 * @param pricePerKwh Geld je kWh der bezahlten Ladungen (Kosten über 0 mit Energie), null ohne solche
 * @param lastLogId   neueste Ladung des Orts, zum Nachtragen des Betreibers
 * @param cpoName     häufigster eingetragener Betreiber, null ohne Angabe
 * @param chargingType häufigste Ladeart (AC/DC) dieses Betreibers, ohne Betreiber des ganzen Orts
 */
public record ChargingPlaceResponse(
        String geohash,
        Kind kind,
        int charges,
        BigDecimal kwh,
        BigDecimal costEur,
        BigDecimal pricePerKwh,
        LocalDateTime lastChargedAt,
        UUID lastLogId,
        String cpoName,
        ChargingType chargingType) {

    /** HOME = häufigster privater Ort des Nutzers, UNKNOWN = ohne Angabe öffentlich/privat. */
    public enum Kind { HOME, PRIVATE, PUBLIC, UNKNOWN }
}
