package com.evmonitor.application.seo;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Seitentyp aus dem Pfad der deutschen Website. APP sind Seiten hinter dem Login und technische Pfade: Sie haben
 * Einstiege (PWA, Lesezeichen), sind aber keine Landingpages und gehören nicht in die SEO-Auswertung. Die Liste der
 * App-Pfade folgt den Routen mit requiresAuth bzw. ohne öffentlichen Inhalt in frontend/src/router/index.ts.
 */
public enum SeoPageType {
    HOME, MODEL_LIST, BRAND, MODEL, CONTENT, CONVERSION, LEGAL, OTHER, APP;

    private static final List<String> APP_PREFIXES = List.of(
            "/erfassen", "/dashboard", "/story", "/logs", "/statistics", "/cars", "/charging-providers", "/settings",
            "/coins", "/wallbox", "/imports", "/live", "/upgrade", "/supporter", "/tax-export", "/umfrage",
            "/forgot-password", "/reset-password", "/verify-email", "/auth", "/fahrzeug", "/ladekurve", "/admin",
            "/stories/edit", "/swagger-ui", "/v3", "/api");
    private static final List<String> CONTENT_PREFIXES = List.of(
            "/blog", "/stories", "/consumption-methodology", "/leaderboard", "/rankings");
    private static final List<String> CONVERSION_PREFIXES = List.of("/register", "/login", "/preise");
    private static final List<String> LEGAL_PREFIXES = List.of("/datenschutz", "/impressum", "/agb", "/terms");
    private static final List<String> HOME_PREFIXES = List.of("/landing", "/start");
    private static final Pattern MODEL_PAGE = Pattern.compile("^/modelle/[^/]+/[^/]+$");
    private static final Pattern BRAND_PAGE = Pattern.compile("^/modelle/[^/]+$");

    public static SeoPageType of(String path) {
        if (path != null && SeoMarket.isInternational(path)) path = withoutMarketPrefix(path);
        if (path == null || path.isBlank() || path.equals("/")) return HOME;
        if (matches(path, APP_PREFIXES)) return APP;
        if (path.equals("/modelle") || path.equals("/modelle/vergleich")) return MODEL_LIST;
        if (MODEL_PAGE.matcher(path).matches()) return MODEL;
        if (BRAND_PAGE.matcher(path).matches()) return BRAND;
        if (matches(path, CONTENT_PREFIXES)) return CONTENT;
        if (matches(path, CONVERSION_PREFIXES)) return CONVERSION;
        if (matches(path, LEGAL_PREFIXES)) return LEGAL;
        if (matches(path, HOME_PREFIXES)) return HOME;
        return OTHER;
    }

    /** "/en/login" -> "/login", "/en" -> "/". */
    private static String withoutMarketPrefix(String path) {
        int second = path.indexOf('/', 1);
        return second < 0 ? "/" : path.substring(second);
    }

    public boolean isLandingpage() {
        return this != APP;
    }

    private static boolean matches(String path, List<String> prefixes) {
        for (String prefix : prefixes) {
            if (path.equals(prefix) || path.startsWith(prefix + "/")) return true;
        }
        return false;
    }
}
