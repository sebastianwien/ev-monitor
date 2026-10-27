package com.evmonitor.application.tariff.crawl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

/**
 * Prüft ein Zitat des Sprachmodells gegen den Seitentext: Es muss dort stehen (Leerraum egal) und
 * den Preis enthalten, als "0,39", "0.39" oder "39 ct". Ein erfundener oder aus der falschen Zeile
 * kopierter Preis fällt so heraus.
 */
public final class EvidenceCheck {

    private EvidenceCheck() {}

    public static boolean supports(String pageText, String quote, BigDecimal pricePerKwh) {
        if (quote == null || quote.isBlank() || pricePerKwh == null) return false;
        String q = squash(quote);
        if (!squash(pageText).contains(q)) return false;
        return spellings(pricePerKwh).stream().anyMatch(q::contains);
    }

    /** Ein Betreibername steht wörtlich auf der Seite (Groß- und Kleinschreibung egal). */
    public static boolean mentions(String pageText, String name) {
        return name != null && !name.isBlank() && squash(pageText).contains(squash(name));
    }

    /** Kleinbuchstaben ohne Leerraum: "AC : 0,52 €" und "AC: 0,52 €" werden gleich. */
    private static String squash(String s) {
        return s.toLowerCase(Locale.ROOT).replace(' ', ' ').replaceAll("\\s+", "");
    }

    private static List<String> spellings(BigDecimal price) {
        BigDecimal euros = price.setScale(2, RoundingMode.HALF_UP);
        String dot = euros.toPlainString();
        String cents = euros.movePointRight(2).stripTrailingZeros().toPlainString();
        return List.of(dot.replace('.', ','), dot, cents + "ct", cents + "cent");
    }
}
