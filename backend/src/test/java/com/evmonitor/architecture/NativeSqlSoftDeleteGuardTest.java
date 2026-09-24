package com.evmonitor.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code ev_log} ist soft-deleted. JPQL und Derived Queries filtert Hibernate über
 * {@code @SQLRestriction} automatisch, natives SQL nicht. Jedes native String-Literal, das
 * {@code ev_log} per FROM, JOIN oder UPDATE anspricht, muss deshalb selbst
 * {@code deleted_at IS NULL} enthalten.
 *
 * Bewusste Ausnahmen (Dedupe gegen Re-Import, Restore, Soft-Delete selbst) stehen unmittelbar
 * über dem Literal mit dem Kommentar {@code // soft-delete-bypass: <Grund>}.
 */
class NativeSqlSoftDeleteGuardTest {

    static final String BYPASS_MARKER = "soft-delete-bypass";

    /** Ein Text-Block oder ein einfacher String-Literal. */
    private static final Pattern STRING_LITERAL = Pattern.compile(
            "\"\"\"(.*?)\"\"\"|\"((?:[^\"\\\\\\n]|\\\\.)*)\"", Pattern.DOTALL);

    @Test
    void everyNativeEvLogQueryFiltersDeletedRows() throws IOException {
        List<String> offenders = scan("ev_log");
        assertTrue(offenders.isEmpty(),
                "Natives SQL auf ev_log ohne 'deleted_at IS NULL' (oder Marker '// " + BYPASS_MARKER + "'):\n"
                        + String.join("\n", offenders));
    }

    /**
     * Gleiche Regel für die anderen soft-gelöschten Tabellen. Geprüft wird nur die Tabelle selbst:
     * ob eine ev_log-Query auch das Auto auf {@code c.deleted_at IS NULL} prüft, sieht dieser Test nicht.
     */
    @Test
    void everyNativeCarAndTripQueryFiltersDeletedRows() throws IOException {
        List<String> offenders = new ArrayList<>(scan("car"));
        offenders.addAll(scan("ev_trip"));
        assertTrue(offenders.isEmpty(), String.join("\n", offenders));
    }

    /** SQL-Schlüsselwörter, die nach dem Tabellennamen stehen können und kein Alias sind. */
    private static final java.util.Set<String> KEYWORDS = java.util.Set.of(
            "WHERE", "SET", "JOIN", "LEFT", "RIGHT", "INNER", "ON", "GROUP", "ORDER", "USING", "AS");

    static List<String> scan(String table) throws IOException {
        // Großgeschriebene Schlüsselwörter: so unterscheidet sich SQL von Prosa wie "from car display".
        Pattern tableRef = Pattern.compile("\\b(FROM|JOIN|UPDATE)\\s+" + table + "\\b(?!_)(?:\\s+(\\w+))?");
        Path root = Path.of("src/main/java");
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String source = Files.readString(p);
                Matcher m = STRING_LITERAL.matcher(source);
                while (m.find()) {
                    String literal = m.group(1) != null ? m.group(1) : m.group(2);
                    if (literal == null) continue;
                    Matcher ref = tableRef.matcher(literal);
                    while (ref.find()) {
                        String alias = ref.group(2) != null && !KEYWORDS.contains(ref.group(2).toUpperCase()) ? ref.group(2) : null;
                        // Der Filter muss auf genau diesem Alias stehen. "c.deleted_at IS NULL" vom Car-Join zählt nicht.
                        String prefix = alias != null ? Pattern.quote(alias + ".") : "(?<![\\w.])";
                        Pattern filter = Pattern.compile("(?<![\\w.])" + prefix + "deleted_at\\s+IS\\s+NULL", Pattern.CASE_INSENSITIVE);
                        if (filter.matcher(literal).find()) continue;
                        if (hasBypassMarker(source, m.start())) continue;
                        int line = 1 + (int) source.substring(0, m.start() + ref.start()).chars().filter(c -> c == '\n').count();
                        offenders.add(root.relativize(p) + ":" + line + " (" + table + (alias != null ? " " + alias : "") + ")");
                    }
                }
            }
        }
        return offenders;
    }

    /** Marker zählt, wenn er in den 400 Zeichen vor dem Literal steht und dazwischen kein weiteres Literal liegt. */
    private static boolean hasBypassMarker(String source, int literalStart) {
        int from = Math.max(0, literalStart - 400);
        String before = source.substring(from, literalStart);
        int marker = before.lastIndexOf(BYPASS_MARKER);
        if (marker < 0) return false;
        return !before.substring(marker).contains("\"");
    }
}
