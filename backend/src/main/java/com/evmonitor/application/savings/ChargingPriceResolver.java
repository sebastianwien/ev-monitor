package com.evmonitor.application.savings;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

/**
 * Die beiden Preisketten der Heimlade-Ersparnis.
 *
 * Reine Rechenlogik ohne Datenbankzugriff - der Service laedt die Kandidaten und
 * reicht sie hier hinein. Beide Ketten liefern immer die verwendete Stufe mit, damit
 * die Kachel benennen kann, worauf die Zahl beruht.
 *
 * Die Ketten sind bewusst asymmetrisch: der oeffentliche Preis faellt am Ende auf den
 * Landes-Median zurueck, weil das ein Fremdvergleich ist. Der Heimpreis tut das nicht -
 * ueber die Stromkosten des Nutzers wird nichts behauptet, was er nicht hinterlegt hat.
 */
public final class ChargingPriceResolver {

    /** Ab so vielen eigenen Ladungen traegt der eigene Median. Darunter ist er zu zufaellig. */
    static final int MIN_OWN_HOME_LOGS = 3;
    static final int MIN_OWN_PUBLIC_LOGS = 5;

    /** Fenster plausibler Preise je kWh. Darueber Tippfehler, darunter Rechenartefakte. */
    static final BigDecimal MAX_PLAUSIBLE = new BigDecimal("2.00");
    static final BigDecimal MIN_PLAUSIBLE_PUBLIC = new BigDecimal("0.01");

    private ChargingPriceResolver() {}

    /**
     * Heimpreis: eigene Log-Preise, sonst die als Heimstrom markierte Ladekarte.
     *
     * Einen eigenen Zweig fuer Wallbox-Tarife braucht es nicht: go-e und OCPP schreiben
     * ihren Tarif bereits auf jedes Log, der Wert steckt also in den Log-Preisen.
     *
     * Nulltarife zaehlen als echter Wert - PV-Ueberschuss kostet nichts, und ein Filter
     * auf "> 0" wuerde ausgerechnet dieser Gruppe die Ersparnis kleinrechnen.
     */
    public static PriceBasis resolveHomePrice(List<BigDecimal> ownLogPrices,
                                              BigDecimal homeCardPrice) {
        List<BigDecimal> plausible = plausible(ownLogPrices, BigDecimal.ZERO);
        if (plausible.size() >= MIN_OWN_HOME_LOGS) {
            return new PriceBasis(PriceSource.OWN_LOGS, median(plausible), plausible.size());
        }
        if (homeCardPrice != null) {
            return new PriceBasis(PriceSource.HOME_CARD, homeCardPrice, 1);
        }
        return PriceBasis.NONE;
    }

    /**
     * Oeffentlicher Preis: eigene Ladungen, sonst Umgebung, sonst Land.
     *
     * Region und Land ermittelt der Service - hier faellt nur die Reihenfolge. Beide
     * kommen als Supplier herein, damit die Datenbank nur befragt wird, wenn die Stufe
     * tatsaechlich an die Reihe kommt. Eine zu duenne Regionsstufe liefert {@code null}
     * und faellt damit geraeuschlos durch.
     */
    public static PriceBasis resolvePublicPrice(List<BigDecimal> ownLogPrices,
                                                Supplier<PriceBasis> region,
                                                Supplier<PriceBasis> country) {
        List<BigDecimal> plausible = plausible(ownLogPrices, MIN_PLAUSIBLE_PUBLIC);
        if (plausible.size() >= MIN_OWN_PUBLIC_LOGS) {
            return new PriceBasis(PriceSource.OWN_PUBLIC, median(plausible), plausible.size());
        }
        PriceBasis fromRegion = region != null ? region.get() : null;
        if (fromRegion != null && fromRegion.isKnown()) return fromRegion;
        PriceBasis fromCountry = country != null ? country.get() : null;
        if (fromCountry != null && fromCountry.isKnown()) return fromCountry;
        return PriceBasis.NONE;
    }

    private static List<BigDecimal> plausible(List<BigDecimal> prices, BigDecimal lowerBound) {
        if (prices == null) return List.of();
        return prices.stream()
                .filter(p -> p != null)
                .filter(p -> p.compareTo(lowerBound) >= 0 && p.compareTo(MAX_PLAUSIBLE) <= 0)
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    /** Median statt Mittelwert: eine einzelne Ionity-Ladung soll den Wert nicht kippen. */
    static BigDecimal median(List<BigDecimal> sorted) {
        int n = sorted.size();
        if (n == 0) return null;
        if (n % 2 == 1) return sorted.get(n / 2);
        return sorted.get(n / 2 - 1).add(sorted.get(n / 2))
                .divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
    }
}
