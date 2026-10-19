package com.evmonitor.application.seo;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Ein Schlüssel für alle SEO-Quellen: der normalisierte Seitenpfad. GSC liefert volle URLs,
 * Plausible nur Pfade, beide landen hier auf derselben Form, sonst zerfällt eine Modellseite
 * in mehrere Varianten. Schreibung bleibt erhalten, weil die Modell-URLs kanonisch groß
 * geschrieben sind.
 */
public final class SeoPath {

    public static final int MAX_LENGTH = 500;

    private static final Pattern BRAND = Pattern.compile("ev.?monitor", Pattern.CASE_INSENSITIVE);

    private SeoPath() {
    }

    public static String normalize(String urlOrPath) {
        if (urlOrPath == null || urlOrPath.isBlank()) return "/";
        String path = rawPath(urlOrPath.trim());
        path = URLDecoder.decode(path.replace("+", "%2B"), StandardCharsets.UTF_8);
        path = path.replace(' ', '_');
        int hash = path.indexOf('#');
        if (hash >= 0) path = path.substring(0, hash);
        while (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);
        if (path.isEmpty() || !path.startsWith("/")) path = "/" + path;
        return path.length() > MAX_LENGTH ? path.substring(0, MAX_LENGTH) : path;
    }

    private static String rawPath(String value) {
        if (!value.contains("://")) {
            int q = value.indexOf('?');
            return q >= 0 ? value.substring(0, q) : value;
        }
        try {
            String raw = URI.create(value).getRawPath();
            return raw == null ? "/" : raw;
        } catch (IllegalArgumentException e) {
            // URI mit Leerzeichen: Schema und Host von Hand abschneiden
            String rest = value.substring(value.indexOf("://") + 3);
            int slash = rest.indexOf('/');
            String path = slash >= 0 ? rest.substring(slash) : "/";
            int q = path.indexOf('?');
            return q >= 0 ? path.substring(0, q) : path;
        }
    }

    public static boolean isBrand(String query) {
        return query != null && BRAND.matcher(query).find();
    }
}
