package com.evmonitor.application.tariff.crawl;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Zitat des Sprachmodells mit dem Seitentext davor und danach, damit der Admin sieht, wo ein Preis steht.
 * {@code match} ist der Text der Seite selbst; gefunden wird das Zitat wie in {@link EvidenceCheck}
 * (Leerraum und Groß-/Kleinschreibung egal). Zeilenumbrüche werden zu " · ".
 *
 * <p>Gespeichert in der Spalte {@code evidence}, die drei Teile getrennt durch das Steuerzeichen US.
 * Ein Eintrag ohne Trenner (vor diesem Format) ist nur das Zitat.
 */
public record EvidenceSnippet(String before, String match, String after) {

    private static final char SEPARATOR = '\u001F';
    private static final String BREAK = " · ";

    /**
     * @param nearName Tarifname: steht dasselbe Zitat mehrfach auf der Seite (Tabellen), gewinnt die Stelle mit dem
     *                 Namen in der Nähe; ohne Namen oder Treffer die erste.
     */
    public static EvidenceSnippet find(String pageText, String quote, int radius, String nearName) {
        String q = quote == null ? "" : quote.replace(SEPARATOR, ' ').replaceAll("\\s+", " ").trim();
        Matcher m = q.isEmpty() || pageText == null ? null : pattern(q).matcher(pageText);
        if (m == null || !m.find()) return new EvidenceSnippet("", q, "");
        int start = m.start();
        int end = m.end();
        String name = nearName == null || nearName.isBlank() ? null : nearName.toLowerCase(Locale.ROOT);
        if (name != null && !near(pageText, start, end, radius, name)) {
            do {
                if (near(pageText, m.start(), m.end(), radius, name)) {
                    start = m.start();
                    end = m.end();
                    break;
                }
            } while (m.find());
        }
        String before = pageText.substring(0, start);
        String after = pageText.substring(end);
        if (before.length() > radius) before = "…" + fromFirstSpace(before.substring(before.length() - radius));
        if (after.length() > radius) after = toLastSpace(after.substring(0, radius)) + "…";
        return new EvidenceSnippet(flat(before), flat(pageText.substring(start, end)), flat(after));
    }

    private static boolean near(String pageText, int start, int end, int radius, String name) {
        return pageText.substring(Math.max(0, start - radius), Math.min(pageText.length(), end + radius))
                .toLowerCase(Locale.ROOT).contains(name);
    }

    public String encode() {
        return before + SEPARATOR + match + SEPARATOR + after;
    }

    public static EvidenceSnippet decode(String stored) {
        if (stored == null) return null;
        String[] parts = stored.split(String.valueOf(SEPARATOR), 3);
        return parts.length == 3 ? new EvidenceSnippet(parts[0], parts[1], parts[2]) : new EvidenceSnippet("", stored, "");
    }

    /** Jedes Zeichen des Zitats der Reihe nach, beliebiger Leerraum dazwischen. */
    private static Pattern pattern(String quote) {
        StringBuilder regex = new StringBuilder();
        quote.codePoints().filter(c -> !Character.isWhitespace(c) && c != ' ').forEach(c -> {
            if (!regex.isEmpty()) regex.append("[\\s\\u00A0]*");
            regex.append(Pattern.quote(new String(Character.toChars(c))));
        });
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    private static String fromFirstSpace(String s) {
        int i = indexOfSpace(s);
        return i < 0 ? s : s.substring(i);
    }

    private static String toLastSpace(String s) {
        for (int i = s.length() - 1; i >= 0; i--) {
            if (Character.isWhitespace(s.charAt(i))) return s.substring(0, i);
        }
        return s;
    }

    private static int indexOfSpace(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isWhitespace(s.charAt(i))) return i;
        }
        return -1;
    }

    private static String flat(String s) {
        return s.replace(SEPARATOR, ' ').replaceAll("[ \\t\\u00A0]*\\R[\\s\\u00A0]*", BREAK).replaceAll("[ \\t\\u00A0]+", " ");
    }
}
