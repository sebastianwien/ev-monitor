package com.evmonitor.application;

import com.evmonitor.domain.ChargingSite;
import com.evmonitor.domain.ChargingType;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Welche Register-Säule zu einer Ladung gehört - oder keine, wenn das nicht eindeutig ist.
 *
 * Die Position einer Ladung ist eine 7-stellige Geohash-Zelle (~150 m), die Entfernung zur Säule
 * also auf etwa 75 m ungenau. Eine Säule auf der Zellgrenze landet deshalb mal in der einen, mal
 * in der anderen Zelle. Entschieden wird darum nicht über die Zelle, sondern in dieser Reihenfolge:
 * Umkreis, Technik, Eindeutigkeit, Historie des Nutzers, klarer Abstand. Was danach noch offen ist,
 * bleibt offen - eine falsch zugeordnete Säule zieht einen falschen Preis nach sich.
 */
public final class ChargingSiteMatcher {

    /** Weiter weg steht das Auto nicht an dieser Säule, auch mit Zell-Ungenauigkeit. */
    static final int MAX_DISTANCE_METERS = 150;

    /** Die nächste Säule gewinnt nur, wenn sie weniger als halb so weit weg ist wie die zweite. */
    static final double DISTANCE_RATIO = 0.5;

    /** Fahrzeuge melden etwas mehr als die Säule laut Register hergibt (11,4 kW an 11 kW). */
    private static final BigDecimal POWER_TOLERANCE = new BigDecimal("1.1");

    private ChargingSiteMatcher() {
    }

    /**
     * @param station    die zugeordnete Säule, null wenn keine eindeutig ist
     * @param candidates die Säulen, die nach Umkreis und Technik in Frage kamen, die nächste zuerst
     */
    public record Result(NearbyStation station, List<NearbyStation> candidates) {
    }

    /**
     * @param nearby        Register-Säulen um die Zelle der Ladung
     * @param usedSiteKeys  {@link #siteKey} der Standorte, an denen der Nutzer schon geladen hat
     */
    public static Result pick(ChargingType chargingType, BigDecimal maxPowerKw,
                              List<NearbyStation> nearby, Set<String> usedSiteKeys) {
        List<NearbyStation> candidates = nearby.stream()
                .filter(s -> s.distanceMeters() <= MAX_DISTANCE_METERS)
                .filter(s -> offers(s, chargingType))
                .filter(s -> delivers(s, chargingType, maxPowerKw))
                .sorted(Comparator.comparingInt(NearbyStation::distanceMeters))
                .toList();

        if (candidates.size() == 1) return new Result(candidates.get(0), candidates);
        if (candidates.isEmpty()) return new Result(null, candidates);

        List<NearbyStation> used = candidates.stream()
                .filter(s -> usedSiteKeys.contains(siteKey(s.geohash(), s.name())))
                .toList();
        if (used.size() == 1) return new Result(used.get(0), candidates);

        NearbyStation nearest = candidates.get(0);
        NearbyStation second = candidates.get(1);
        if (nearest.distanceMeters() < second.distanceMeters() * DISTANCE_RATIO) {
            return new Result(nearest, candidates);
        }
        return new Result(null, candidates);
    }

    /** Gleicher Schlüssel wie der Standort selbst: Zelle plus Name ohne Groß-/Kleinschreibung. */
    public static String siteKey(String geohash, String name) {
        return geohash + "|" + ChargingSite.nameKey(name);
    }

    /** Eine Säule ohne Angabe für diese Ladeart bietet sie nur dann nicht, wenn sie die andere hat. */
    private static boolean offers(NearbyStation s, ChargingType type) {
        if (type == ChargingType.AC) return s.maxAcKw() != null || s.maxDcKw() == null;
        if (type == ChargingType.DC) return s.maxDcKw() != null || s.maxAcKw() == null;
        return true;
    }

    private static boolean delivers(NearbyStation s, ChargingType type, BigDecimal maxPowerKw) {
        if (maxPowerKw == null) return true;
        Double limit = type == ChargingType.AC ? s.maxAcKw()
                : type == ChargingType.DC ? s.maxDcKw()
                : max(s.maxAcKw(), s.maxDcKw());
        if (limit == null) return true;
        return maxPowerKw.compareTo(BigDecimal.valueOf(limit).multiply(POWER_TOLERANCE)) <= 0;
    }

    private static Double max(Double a, Double b) {
        if (a == null) return b;
        if (b == null) return a;
        return Math.max(a, b);
    }
}
