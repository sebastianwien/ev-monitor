package com.evmonitor.application.seo;

import java.util.List;

/**
 * Markt einer Seite, abgeleitet aus dem Pfadpräfix. Seit Oktober 2026 ist nur die deutsche Website im Index; die
 * Sprachvarianten tragen noindex. DE = alles ohne Sprachpräfix (auch Startseite, Registrierung, Blog).
 */
public enum SeoMarket {
    ALL, DE, INTL;

    /** Pfadpräfixe der Sprachvarianten, siehe useMarketRoute im Frontend. */
    public static final List<String> INTL_PREFIXES = List.of("/en", "/gb", "/no", "/se", "/us");

    public static boolean isInternational(String path) {
        if (path == null) return false;
        for (String prefix : INTL_PREFIXES) {
            if (path.equals(prefix) || path.startsWith(prefix + "/")) return true;
        }
        return false;
    }

    public boolean includes(String path) {
        return switch (this) {
            case ALL -> true;
            case DE -> !isInternational(path);
            case INTL -> isInternational(path);
        };
    }
}
