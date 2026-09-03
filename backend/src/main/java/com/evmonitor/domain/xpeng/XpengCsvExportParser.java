package com.evmonitor.domain.xpeng;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
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
 * <p><b>Zeitzone:</b> {@code timer} ist ein absoluter Epoch-Zeitstempel; er wird
 * hier in UTC nach {@link LocalDateTime} umgewandelt. Ob die spaeter angezeigten
 * Trip-/Ladezeiten die Fahrzeug-Lokalzeit treffen sollen, ist gegen echte Fahrten
 * zu verifizieren (das {@code ds}-Feld ist nur ein Batch-Label, nicht das
 * Kalenderdatum der Zeile).
 *
 * <p>Security: begrenzte Entry-Zahl und Gesamtgroesse gegen Zip-Bombs; nur
 * {@code .csv}-Entries ohne Pfadanteile werden gelesen.
 */
@Slf4j
public class XpengCsvExportParser {

    private static final int MAX_ENTRIES = 50;
    private static final long MAX_TOTAL_UNCOMPRESSED_BYTES = 300L * 1024 * 1024; // 300 MB
    private static final int MAX_TIMERS = 2_000_000;
    // timer ist ein absoluter Epoch-Zeitstempel. Wir wandeln ihn in die lokale Wanduhrzeit
    // des Fahrzeugs um - konsistent zum alten XLSX-Weg (dort trug XPeng lokale Zeitstrings).
    // Default Europe/Berlin fuer die aktuelle Nutzerbasis; spaeter ggf. pro Fahrzeug.
    private static final ZoneId EXPORT_ZONE = ZoneId.of("Europe/Berlin");

    public record ParseResult(XpengVehicleInfo vehicleInfo, long rowsProcessed) {}

    /**
     * Liest das ZIP, joint die CSV-Cluster ueber {@code timer} und reicht jede
     * zusammengefuehrte Zeile in Zeitreihenfolge an {@code rowHandler}.
     *
     * @throws XpengParseException wenn die Pflichtsignale ({@link XpengHeaderMapper#REQUIRED_LOGICAL})
     *                             ueber alle CSVs hinweg nicht aufloesbar sind (Schema-Drift).
     */
    public ParseResult parse(Path zipPath, Consumer<XpengTelematicsRow> rowHandler) throws Exception {
        // epoch -> (logical -> Rohwert), ueber alle Cluster gemerged. TreeMap haelt die Zeitreihenfolge.
        TreeMap<Long, Map<String, String>> byTimer = new TreeMap<>();
        List<String> resolvedLogicals = new ArrayList<>();
        String vin = null;
        String vmodel = null;
        long totalBytes = 0;
        int entryCount = 0;

        try (InputStream fis = new BufferedInputStream(Files.newInputStream(zipPath));
             ZipInputStream zis = new ZipInputStream(fis, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                if (name.contains("/") || name.contains("\\") || name.contains("..")) {
                    throw new XpengParseException("ZIP-Entry mit Pfadanteil abgelehnt: " + name);
                }
                if (!name.toLowerCase().endsWith(".csv")) continue;
                if (++entryCount > MAX_ENTRIES) {
                    throw new XpengParseException("ZIP hat zu viele Entries (> " + MAX_ENTRIES + ")");
                }

                byte[] content = readEntry(zis, MAX_TOTAL_UNCOMPRESSED_BYTES - totalBytes);
                totalBytes += content.length;

                String[] info = mergeCsv(new String(content, StandardCharsets.UTF_8), byTimer, resolvedLogicals);
                if (vin == null) vin = info[0];
                if (vmodel == null) vmodel = info[1];
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

        long emitted = 0;
        for (Map.Entry<Long, Map<String, String>> e : byTimer.entrySet()) {
            LocalDateTime timer = Instant.ofEpochSecond(e.getKey()).atZone(EXPORT_ZONE).toLocalDateTime();
            Map<String, String> values = e.getValue();
            XpengTelematicsRow row = XpengRowMapper.map(values::get, timer);
            rowHandler.accept(row);
            emitted++;
        }
        return new ParseResult(new XpengVehicleInfo(vin, vmodel, null, null, null), emitted);
    }

    /**
     * Liest guenstig die VIN aus dem ZIP, ohne die ~15 MB Telematik komplett zu parsen -
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

    /**
     * Merged eine CSV in {@code byTimer}. Liefert {@code [vin, vmodel]} aus der ersten
     * Datenzeile (oder {@code [null, null]} wenn nicht vorhanden). Ergaenzt neu aufgeloeste
     * logische Felder in {@code resolvedLogicals}.
     */
    private String[] mergeCsv(String content, TreeMap<Long, Map<String, String>> byTimer,
                              List<String> resolvedLogicals) {
        String[] lines = content.split("\r?\n");
        if (lines.length == 0) return new String[]{null, null};

        List<String> headers = splitCsv(stripBom(lines[0]));
        Map<String, Integer> logicalToColumn = XpengHeaderMapper.identifyColumns(headers);
        Integer timerCol = logicalToColumn.get(XpengHeaderMapper.TIMER);
        if (timerCol == null) {
            log.warn("XpengCsvExportParser: CSV ohne 'timer'-Spalte uebersprungen (Header: {})", headers);
            return new String[]{null, null};
        }
        for (String logical : logicalToColumn.keySet()) {
            if (!resolvedLogicals.contains(logical)) resolvedLogicals.add(logical);
        }
        int vinCol = indexOfHeader(headers, "vin");
        int vmodelCol = indexOfHeader(headers, "vmodel");

        String vin = null;
        String vmodel = null;
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].isBlank()) continue;
            List<String> fields = splitCsv(lines[i]);
            Long epoch = parseEpoch(cell(fields, timerCol));
            if (epoch == null) continue;
            if (vin == null && vinCol >= 0) vin = cell(fields, vinCol);
            if (vmodel == null && vmodelCol >= 0) vmodel = cell(fields, vmodelCol);

            Map<String, String> merged = byTimer.get(epoch);
            if (merged == null) {
                if (byTimer.size() >= MAX_TIMERS) {
                    throw new RuntimeException(
                            new XpengParseException("Zu viele Zeitpunkte im Export (> " + MAX_TIMERS + ")"));
                }
                merged = new LinkedHashMap<>();
                byTimer.put(epoch, merged);
            }
            for (Map.Entry<String, Integer> lc : logicalToColumn.entrySet()) {
                String value = cell(fields, lc.getValue());
                if (value != null && !value.isBlank()) merged.put(lc.getKey(), value);
            }
        }
        return new String[]{vin, vmodel};
    }

    private static byte[] readEntry(ZipInputStream zis, long remainingBudget) throws java.io.IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[16 * 1024];
        int n;
        long read = 0;
        while ((n = zis.read(buf)) != -1) {
            read += n;
            if (read > remainingBudget) {
                throw new RuntimeException(
                        new XpengParseException("ZIP-Inhalt ueberschreitet Groessenlimit"));
            }
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
