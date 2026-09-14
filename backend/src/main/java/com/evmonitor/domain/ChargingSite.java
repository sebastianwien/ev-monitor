package com.evmonitor.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

/**
 * Ein oeffentlicher Ladestandort: ein Betreiber in einer Geohash-Zelle (7 Stellen, ~150 m).
 * Geteilt zwischen allen Nutzern - die Beziehung zum Nutzer entsteht nur ueber seine Logs.
 *
 * @param cpoName Name im Ladenetz-Katalog (charging_networks), null wenn der Betreiber dort fehlt
 */
public record ChargingSite(UUID id, String name, String cpoName, String geohash, BigDecimal maxPowerKw,
                           int chargePoints, boolean fastCharging, ChargingSiteSource source,
                           LocalDateTime createdAt) {

    /** Vergleichsschluessel: Gross-/Kleinschreibung und Rand-Leerraum spielen keine Rolle. */
    public static String nameKey(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
