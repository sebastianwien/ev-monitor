package com.evmonitor.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Leitplanke D15: Importe schreiben {@code ev_log} und {@code ev_trip} nur über das
 * {@code IngestGateway}, und im Gateway entscheidet die Policy-Tabelle, nicht ein Vergleich auf
 * die Quelle. Bearbeitungen bestehender Zeilen tragen den Marker {@value #BYPASS_MARKER}; offene
 * Importe stehen benannt in {@code PENDING} und schrumpfen je Release (R2g, R2h).
 */
class IngestGuardTest {

    private static final Path MAIN = Path.of("src/main/java/com/evmonitor");

    /** Schreibt eine neue Zeile oder überschreibt eine bestehende in ev_log/ev_trip. */
    private static final Pattern WRITE = Pattern.compile(
            "\\b(?:evLogRepository|evTripRepository|tripRepository)\\.save(?:All)?\\("
                    + "|\\bevLogService\\.save\\("
                    + "|INSERT\\s+INTO\\s+ev_(?:log|trip)\\b",
            Pattern.CASE_INSENSITIVE);

    /** Die Tür selbst und die Heimat-Services für manuell erfasste und bearbeitete Daten. */
    private static final Map<String, String> ALLOWED = Map.of(
            "application/ingest/IngestGateway.java", "die Tür",
            "application/EvLogWriter.java", "Speicher-Primitive mit Events",
            "application/EvLogService.java", "manuelles Loggen, Bearbeiten, Zusammenführen",
            "application/TripService.java", "manuelle Fahrten, Bearbeiten, Zusammenführen");

    /**
     * Einzelne Schreibstellen anderswo, die bestehende Zeilen bearbeiten statt Importe anzulegen,
     * tragen diesen Marker in der Zeile oder bis zu drei Zeilen darüber, mit Grund.
     */
    static final String BYPASS_MARKER = "ingest-bypass:";

    /** Importe, die noch nicht durchs Gateway laufen. Jede Zeile hier ist offene Arbeit. */
    private static final Map<String, String> PENDING = Map.of(
            "application/spritmonitor/SpritMonitorImportService.java", "R2g",
            "application/publicapi/PublicApiTripService.java", "R2g",
            "application/tessie/TessieProcessorService.java", "R2h");

    private static final Pattern SOURCE_COMPARISON = Pattern.compile(
            "DataSource\\.[A-Z_]+\\s*[!=]=|[!=]=\\s*DataSource\\.[A-Z_]+"
                    + "|DataSource\\.[A-Z_]+\\.equals\\(|\\.equals\\(\\s*DataSource\\.[A-Z_]+");

    @Test
    void importsWriteEvLogAndEvTripOnlyThroughTheGateway() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles(MAIN)) {
            String rel = MAIN.relativize(file).toString();
            if (ALLOWED.containsKey(rel) || PENDING.containsKey(rel)) continue;
            String source = Files.readString(file);
            Matcher m = WRITE.matcher(source);
            while (m.find()) {
                if (hasBypassMarker(source, m.start())) continue;
                int line = 1 + (int) source.substring(0, m.start()).chars().filter(c -> c == '\n').count();
                offenders.add(rel + ":" + line + " → " + m.group());
            }
        }
        assertThat(offenders).as("Schreibzugriff auf ev_log/ev_trip außerhalb des IngestGateway").isEmpty();
    }

    /** Wer einen Import aufs Gateway umstellt, streicht ihn hier; sonst bleibt die Liste zu groß. */
    @Test
    void pendingExceptionsStillWrite() throws IOException {
        for (String rel : PENDING.keySet()) {
            assertThat(WRITE.matcher(Files.readString(MAIN.resolve(rel))).find())
                    .as("%s schreibt nicht mehr selbst, Ausnahme aus PENDING streichen", rel)
                    .isTrue();
        }
    }

    @Test
    void gatewayDecidesByPolicyNotBySourceComparison() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles(MAIN.resolve("application/ingest"))) {
            Matcher m = SOURCE_COMPARISON.matcher(Files.readString(file));
            while (m.find()) offenders.add(MAIN.relativize(file) + " → " + m.group());
        }
        assertThat(offenders).as("Quellenvergleich im Gateway, gehört in IngestPolicies").isEmpty();
    }

    private static boolean hasBypassMarker(String source, int writeStart) {
        int lineEnd = source.indexOf('\n', writeStart);
        int from = writeStart;
        for (int i = 0; i < 4 && from > 0; i++) {
            from = source.lastIndexOf('\n', from - 1);
        }
        return source.substring(Math.max(0, from), lineEnd < 0 ? source.length() : lineEnd).contains(BYPASS_MARKER);
    }

    private static List<Path> javaFiles(Path root) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(f -> f.toString().endsWith(".java")).toList();
        }
    }
}
