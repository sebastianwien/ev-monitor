package com.evmonitor.application.voice;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Baut die Bias-Liste fuer die Transkription aus den Namen, die in diesem Request plausibel sind:
 * Ort-Kandidaten, eigene Tarife, Betreiber der letzten Logs, dazu wenige Fachbegriffe. Eine globale
 * Namensliste verschlechtert laut Messung die Treffer.
 *
 * <p>Mistral nimmt nur Einzelwoerter: ein Begriff mit Leerzeichen oder Komma laesst den ganzen Request
 * mit 400 scheitern, Unterstrich-Phrasen landen woertlich im Transkript. Darum werden Namen bereinigt
 * und in Woerter geteilt.
 */
public final class BiasTermBuilder {

    /** Mistral nennt bis zu 100 Begriffe. */
    public static final int MAX_TERMS = 100;

    static final List<String> DOMAIN_TERMS = List.of("kWh", "Kilowattstunden", "Tacho", "Kilometerstand",
            "Ladestand", "SoC", "Wallbox", "Supercharger", "Schnelllader", "Ladekarte");

    private static final Pattern BRACKETS = Pattern.compile("\\(.*?\\)");
    private static final Pattern LEGAL_FORMS = Pattern.compile(
            "\\b(GmbH|mbH|AG|KG|SE|Co\\.?|e\\.V\\.|Dienstleistung|Germany|Austria)\\b|&",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern NOT_ALLOWED = Pattern.compile("[^\\p{L}\\p{N}.+\\-]");
    private static final Set<String> STOP_WORDS = Set.of("und", "der", "die", "das", "bei", "an", "am", "im",
            "für", "von", "me", "the");

    private BiasTermBuilder() {}

    public static List<String> build(List<String> candidateNames, List<String> tariffNames, List<String> recentOperators) {
        List<String> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (List<String> source : List.of(nullSafe(candidateNames), nullSafe(tariffNames), nullSafe(recentOperators))) {
            for (String name : source) {
                for (String word : words(name)) add(word, out, seen);
            }
        }
        DOMAIN_TERMS.forEach(t -> add(t, out, seen));
        return out.size() > MAX_TERMS ? List.copyOf(out.subList(0, MAX_TERMS)) : List.copyOf(out);
    }

    private static List<String> words(String name) {
        if (name == null || name.isBlank()) return List.of();
        String cleaned = BRACKETS.matcher(name).replaceAll(" ").split(",", 2)[0];
        cleaned = LEGAL_FORMS.matcher(cleaned).replaceAll(" ");
        return List.of(cleaned.trim().split("\\s+"));
    }

    private static void add(String raw, List<String> out, Set<String> seen) {
        String term = NOT_ALLOWED.matcher(raw).replaceAll("");
        String key = term.toLowerCase(Locale.ROOT);
        if (term.length() < 2 || STOP_WORDS.contains(key) || !seen.add(key)) return;
        out.add(term);
    }

    private static List<String> nullSafe(List<String> list) {
        return list == null ? List.of() : list;
    }
}
