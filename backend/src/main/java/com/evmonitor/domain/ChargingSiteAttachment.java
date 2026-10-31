package com.evmonitor.domain;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Was die automatische Säulen-Zuordnung an eine Ladung schreibt. Säule, Zelle und "öffentlich"
 * setzt sie immer; Betreiber, Kosten, kWh-Preis und Karte nur, wo die Ladung noch keine hat.
 *
 * @param matchedGeohash Ort der Ladung, für den die Säule gefunden wurde; hat der Nutzer ihn seither
 *                       geändert, passt die Säule nicht mehr und es wird nichts geschrieben
 * @param cpoName        Betreiber der Säule, greift nur ohne Betreiber der Quelle
 * @param costEur        Kosten aus der Historie an der Säule, null wenn keine ableitbar
 */
public record ChargingSiteAttachment(UUID logId, String matchedGeohash, UUID siteId, String geohash, String cpoName,
                                     BigDecimal costEur, BigDecimal pricePerKwh, UUID chargingProviderId) {
}
