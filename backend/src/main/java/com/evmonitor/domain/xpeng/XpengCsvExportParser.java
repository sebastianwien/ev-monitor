package com.evmonitor.domain.xpeng;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Parser fuer das XPeng-EU-Data-Act-CSV-Export-Format (ab 09/2026).
 *
 * XPeng liefert die Fahrzeugdaten nicht mehr als verschluesselte XLSX, sondern
 * als ZIP mit mehreren unverschluesselten CSVs, aufgeteilt nach Signal-Cluster
 * (driving_operation / driving_power_energy / driving_status). Jede Zeile traegt
 * {@code vin,vmodel,timer,ds,<signale...>}; {@code timer} sind Epoch-Sekunden.
 *
 * Die 3 Cluster tragen unterschiedliche Signale - erst ueber {@code timer}
 * zusammengefuehrt ergibt sich eine vollstaendige {@link XpengTelematicsRow}.
 * Spalten werden ueber {@link XpengHeaderMapper} aufgeloest (dieselben physischen
 * Namen wie in der alten XLSX), der Row-Bau teilt sich {@link XpengRowMapper} mit
 * dem XLSX-Parser.
 *
 * <p><b>Speicher (kritisch):</b> Der Join laeuft als <b>k-Wege-Merge ueber die
 * nach {@code timer} sortierten CSV-Streams</b> - es ist immer nur ein Zeitpunkt
 * gleichzeitig im RAM, nie der ganze Export. Ein frueherer Ansatz hielt alle
 * Zeitpunkte in einer {@code TreeMap} und lief bei grossen Exporten (>~100 MB
 * entpackt) in {@code OutOfMemoryError}, was den Import-Thread stumm sterben liess.
 * Voraussetzung des Merge: jede CSV ist in sich nach {@code timer} aufsteigend
 * sortiert - ein Rueckwaertssprung wird mit {@link XpengParseException} gemeldet
 * (kein stiller Fehlmerge).
 *
 * <p><b>Zeitzone:</b> {@code timer} ist ein absoluter Epoch-Zeitstempel; er wird
 * hier in die lokale Wanduhrzeit des Fahrzeugs ({@code Europe/Berlin}) umgewandelt,
 * konsistent zum alten XLSX-Weg (das {@code ds}-Feld ist nur ein Batch-Label).
 *
 * <p>Security: begrenzte Entry-Zahl und entpackte Gesamtgroesse gegen Zip-Bombs;
 * nur {@code .csv}-Entries ohne Pfadanteile werden gelesen.
 */
@Slf4j
public class XpengCsvExportParser {

    private static final int MAX_ENTRIES = 50;
    private static final long MAX_TOTAL_UNCOMPRESSED_BYTES = 300L * 1024 * 1024; // 300 MB
    // timer ist ein absoluter Epoch-Zeitstempel. Wir wandeln ihn in die lokale Wanduhrzeit
    // des Fahrzeugs um - konsistent zum alten XLSX-Weg (dort trug XPeng lokale Zeitstrings).
    // Default Europe/Berlin fuer die aktuelle Nutzerbasis; spaeter ggf. pro Fahrzeug.
    private static final ZoneId EXPORT_ZONE = ZoneId.of("Europe/Berlin");

    public record ParseResult(XpengVehicleInfo vehicleInfo, long rowsProcessed) {}

    /**
     * Liest das ZIP, joint die CSV-Cluster per k-Wege-Merge ueber {@code timer} und
     * reicht jede zusammengefuehrte Zeile in Zeitreihenfolge an {@code rowHandler}.
     *
     * @throws XpengParseException wenn Pflichtsignale ({@link XpengHeaderMapper#REQUIRED_LOGICAL})
     *                             ueber alle CSVs hinweg nicht aufloesbar sind (Schema-Drift),
     *                             eine CSV nicht nach {@code timer} sortiert ist, oder die
     *                             entpackte Groesse das Limit sprengt.
     */
    public ParseResult parse(Path zipPath, Consumer<XpengTelematicsRow> rowHandler) throws Exception {
        List<ClusterReader> readers = new ArrayList<>();
        long[] byteBudget = {MAX_TOTAL_UNCOMPRESSED_BYTES};
        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            int entryCount = 0;
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                if (name.contains("/") || name.contains("\\") || name.contains("..")) {
                    throw new XpengParseException("ZIP-Entry mit Pfadanteil abgelehnt: " + name);
                }
                if (!name.toLowerCase().endsWith(".csv")) continue;
                if (++entryCount > MAX_ENTRIES) {
                    throw new XpengParseException("ZIP hat zu viele Entries (> " + MAX_ENTRIES + ")");
                }
                ClusterReader reader = ClusterReader.open(zip, entry, byteBudget);
                if (reader != null) {
                    readers.add(reader);
                }
            }

            // Pflichtsignale ueber alle Cluster hinweg pruefen, bevor wir Zeilen emittieren.
            List<String> resolvedLogicals = new ArrayList<>();
            for (ClusterReader r : readers) {
                for (String logical : r.logicals()) {
                    if (!resolvedLogicals.contains(logical)) resolvedLogicals.add(logical);
                }
            }
            List<String> missing = new ArrayList<>();
            for (String req : XpengHeaderMapper.REQUIRED_LOGICAL) {
                if (!resolvedLogicals.contains(req)) missing.add(req);
            }
            if (!missing.isEmpty()) {
                throw new XpengParseException(
                        "Pflichtsignale im XPeng-CSV-Export nicht aufloesbar: " + missing
                                + " (aufgeloest: " + resolvedLogicals + ")");
            }

            // Alle Reader auf die erste Datenzeile positionieren.
            for (ClusterReader r : readers) {
                r.advance();
            }

            String vin = null;
            String vmodel = null;
            long emitted = 0;
            while (true) {
                Long min = null;
                for (ClusterReader r : readers) {
                    Long head = r.headEpoch();
                    if (head != null && (min == null || head < min)) min = head;
                }
                if (min == null) break; // alle Streams erschoepft

                Map<String, String> merged = new LinkedHashMap<>();
                for (ClusterReader r : readers) {
                    while (min.equals(r.headEpoch())) {
                        r.mergeHeadInto(merged);
                        if (vin == null && r.vin() != null) vin = r.vin();
                        if (vmodel == null && r.vmodel() != null) vmodel = r.vmodel();
                        r.advance();
                    }
                }

                LocalDateTime timer = Instant.ofEpochSecond(min).atZone(EXPORT_ZONE).toLocalDateTime();
                rowHandler.accept(XpengRowMapper.map(merged::get, timer));
                emitted++;
            }
            return new ParseResult(new XpengVehicleInfo(vin, vmodel, null, null, null), emitted);
        } finally {
            for (ClusterReader r : readers) {
                r.close();
            }
        }
    }

    /**
     * Streamt eine einzelne CSV eines Clusters zeilenweise und haelt genau eine Datenzeile
     * ({@link #headEpoch()}/{@link #mergeHeadInto}) im Speicher. Prueft die Zeit-Sortierung.
     */
    private static final class ClusterReader implements Closeable {
        private final BufferedReader reader;
        private final Map<String, Integer> logicalToColumn;
        private final int timerCol;
        private final int vinCol;
        private final int vmodelCol;
        private final long[] byteBudget;

        private Long headEpoch;
        private List<String> headFields;
        private long lastEpoch = Long.MIN_VALUE;
        private String vin;
        private String vmodel;

        private ClusterReader(BufferedReader reader, Map<String, Integer> logicalToColumn,
                              int timerCol, int vinCol, int vmodelCol, long[] byteBudget) {
            this.reader = reader;
            this.logicalToColumn = logicalToColumn;
            this.timerCol = timerCol;
            this.vinCol = vinCol;
            this.vmodelCol = vmodelCol;
            this.byteBudget = byteBudget;
        }

        /** Oeffnet den Reader und liest den Header. {@code null}, wenn die CSV keine timer-Spalte hat. */
        static ClusterReader open(ZipFile zip, ZipEntry entry, long[] byteBudget) throws Exception {
            InputStream in = zip.getInputStream(entry);
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String headerLine = reader.readLine();
            if (headerLine == null) {
                reader.close();
                return null;
            }
            charge(byteBudget, headerLine);
            List<String> headers = splitCsv(stripBom(headerLine));
            Map<String, Integer> logicalToColumn = XpengHeaderMapper.identifyColumns(headers);
            Integer timerCol = logicalToColumn.get(XpengHeaderMapper.TIMER);
            if (timerCol == null) {
                log.warn("XpengCsvExportParser: CSV ohne 'timer'-Spalte uebersprungen (Header: {})", headers);
                reader.close();
                return null;
            }
            return new ClusterReader(reader, logicalToColumn, timerCol,
                    indexOfHeader(headers, "vin"), indexOfHeader(headers, "vmodel"), byteBudget);
        }

        List<String> logicals() {
            return new ArrayList<>(logicalToColumn.keySet());
        }

        Long headEpoch() {
            return headEpoch;
        }

        String vin() {
            return vin;
        }

        String vmodel() {
            return vmodel;
        }

        /** Liest die naechste gueltige Datenzeile in den Head; setzt {@code headEpoch=null} am Ende. */
        void advance() throws Exception {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                charge(byteBudget, line);
                List<String> fields = splitCsv(line);
                Long epoch = parseEpoch(cell(fields, timerCol));
                if (epoch == null) continue;
                if (epoch < lastEpoch) {
                    throw new XpengParseException("XPeng-Export ist nicht nach Zeit ('timer') sortiert ("
                            + epoch + " < " + lastEpoch + ") - Streaming-Merge nicht moeglich.");
                }
                lastEpoch = epoch;
                if (vin == null && vinCol >= 0) {
                    String v = cell(fields, vinCol);
                    if (v != null) vin = v;
                }
                if (vmodel == null && vmodelCol >= 0) {
                    String v = cell(fields, vmodelCol);
                    if (v != null) vmodel = v;
                }
                headEpoch = epoch;
                headFields = fields;
                return;
            }
            headEpoch = null;
            headFields = null;
        }

        /** Fuehrt die (nicht-leeren) logischen Werte der aktuellen Zeile in {@code target} zusammen. */
        void mergeHeadInto(Map<String, String> target) {
            for (Map.Entry<String, Integer> lc : logicalToColumn.entrySet()) {
                String value = cell(headFields, lc.getValue());
                if (value != null && !value.isBlank()) target.put(lc.getKey(), value);
            }
        }

        @Override
        public void close() {
            try {
                reader.close();
            } catch (Exception ignored) {
                // best effort
            }
        }
    }

    /** Bucht die (ungefaehre) entpackte Groesse gegen das Budget und wirft bei Ueberschreitung. */
    private static void charge(long[] budget, String line) throws XpengParseException {
        budget[0] -= (line.length() + 1L);
        if (budget[0] < 0) {
            throw new XpengParseException("ZIP-Inhalt ueberschreitet Groessenlimit ("
                    + (MAX_TOTAL_UNCOMPRESSED_BYTES / (1024 * 1024)) + " MB)");
        }
    }

    /**
     * Liest guenstig die VIN aus dem ZIP, ohne die Telematik komplett zu parsen -
     * nur Header + erste Datenzeile der ersten CSV mit einer {@code vin}-Spalte. Fuer die
     * Auto-Verknuepfung des Uploads mit dem Fahrzeug.
     *
     * @return die erste gefundene VIN, oder {@code null} wenn keine CSV eine vin-Spalte traegt.
     */
    public static String peekVin(Path zipPath) throws java.io.IOException {
        try (InputStream fis = new BufferedInputStream(Files.newInputStream(zipPath));
             ZipInputStream zis = new ZipInputStream(fis, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                if (name.contains("/") || name.contains("\\") || name.contains("..")) continue;
                if (!name.toLowerCase().endsWith(".csv")) continue;

                // nur den Kopf lesen: Header + erste Datenzeile reichen fuer die VIN.
                byte[] head = readBounded(zis, 64 * 1024);
                String[] lines = new String(head, StandardCharsets.UTF_8).split("\r?\n");
                if (lines.length < 2) continue;
                List<String> headers = splitCsv(stripBom(lines[0]));
                int vinCol = indexOfHeader(headers, "vin");
                if (vinCol < 0) continue;
                for (int i = 1; i < lines.length; i++) {
                    if (lines[i].isBlank()) continue;
                    String vin = cell(splitCsv(lines[i]), vinCol);
                    if (vin != null) return vin;
                }
            }
        }
        return null;
    }

    private static byte[] readBounded(ZipInputStream zis, int limit) throws java.io.IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8 * 1024];
        int n;
        while (bos.size() < limit && (n = zis.read(buf)) != -1) {
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }

    /** Simpler Komma-Split (die Telemetrie-CSV enthaelt keine gequoteten Felder). */
    private static List<String> splitCsv(String line) {
        String[] parts = line.split(",", -1);
        List<String> out = new ArrayList<>(parts.length);
        for (String p : parts) out.add(p);
        return out;
    }

    private static String stripBom(String s) {
        if (s != null && !s.isEmpty() && s.charAt(0) == 0xFEFF) return s.substring(1);
        return s;
    }

    private static int indexOfHeader(List<String> headers, String normalizedName) {
        for (int i = 0; i < headers.size(); i++) {
            if (normalizedName.equals(XpengHeaderMapper.normalize(headers.get(i)))) return i;
        }
        return -1;
    }

    private static String cell(List<String> fields, int col) {
        if (col < 0 || col >= fields.size()) return null;
        String v = fields.get(col);
        return (v == null || v.isBlank()) ? null : v.trim();
    }

    private static Long parseEpoch(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim();
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            try {
                return (long) Double.parseDouble(s);
            } catch (NumberFormatException e2) {
                return null;
            }
        }
    }
}
