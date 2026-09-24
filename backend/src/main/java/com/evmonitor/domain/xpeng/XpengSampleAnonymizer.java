package com.evmonitor.domain.xpeng;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Pseudonymisiert einen XPeng-CSV-Export (ZIP mit den Clustern operation, power_energy, status,
 * ggf. als {@code _part1}-Fortsetzung) fuer die Ablage als Testgrundlage. Personenbezogen ist darin
 * nur die VIN; sie wird ersetzt (WMI bleibt, damit die Marke erkennbar ist), ebenso die Request-ID
 * im Dateinamen. Alle Messwerte bleiben byte-gleich, der Parser liest also dieselben Zeilen.
 *
 * <p>XLSX-Exporte (teils passwortgeschuetzt) werden nicht abgelegt. Uebersteigt die Kopie
 * {@code maxBytes}, wird sie verworfen: die Ablage ist Best Effort und darf den Heap nicht belasten.
 */
public final class XpengSampleAnonymizer {

    public record Result(String fileName, byte[] zip) {}

    private static final Pattern REQUEST_ID = Pattern.compile("(DA\\d{14})([0-9A-Za-z]+)");
    private static final String VIN_CHARS = "ABCDEFGHJKLMNPRSTUVWXYZ0123456789";
    private static final char BOM = '﻿';

    private final long maxBytes;

    public XpengSampleAnonymizer(long maxBytes) {
        this.maxBytes = maxBytes;
    }

    public Optional<Result> anonymize(Path upload, String originalFilename) throws IOException {
        byte[] secret = new byte[16];
        new SecureRandom().nextBytes(secret);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int csvFiles = 0;
        try (InputStream in = Files.newInputStream(upload);
             ZipInputStream zin = new ZipInputStream(in);
             ZipOutputStream zout = new ZipOutputStream(bytes)) {
            for (ZipEntry e; (e = zin.getNextEntry()) != null; ) {
                String name = e.getName();
                if (e.isDirectory() || name.contains("..") || name.contains("/") || !name.contains("_dwd_")
                        || !name.toLowerCase().endsWith(".csv")) continue;
                zout.putNextEntry(new ZipEntry(requestIdToZeros(name)));
                if (!copyCsv(zin, zout, secret, bytes)) return Optional.empty();
                zout.closeEntry();
                csvFiles++;
            }
        } catch (ZipException e) {
            return Optional.empty();
        }
        if (csvFiles == 0) return Optional.empty();
        String name = requestIdToZeros(originalFilename == null ? "xpeng-export.zip" : originalFilename);
        return Optional.of(new Result(name, bytes.toByteArray()));
    }

    /** Kopiert zeilenweise und ersetzt die Spalte {@code vin}. {@code false}, wenn die Grenze reisst. */
    private boolean copyCsv(InputStream csv, ZipOutputStream zout, byte[] secret, ByteArrayOutputStream bytes)
            throws IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(csv, StandardCharsets.UTF_8));
        Writer w = new OutputStreamWriter(zout, StandardCharsets.UTF_8);
        String header = r.readLine();
        if (header == null) return true;
        w.write(header);
        w.write('\n');
        String[] cols = (header.charAt(0) == BOM ? header.substring(1) : header).split(",", -1);
        int vinCol = -1;
        for (int i = 0; i < cols.length; i++) if ("vin".equalsIgnoreCase(cols[i].trim())) vinCol = i;
        String realVin = null;
        String sampleVin = null;
        for (String line; (line = r.readLine()) != null; ) {
            if (vinCol >= 0) {
                String[] parts = line.split(",", -1);
                if (parts.length > vinCol && !parts[vinCol].isEmpty()) {
                    if (!parts[vinCol].equals(realVin)) {
                        realVin = parts[vinCol];
                        sampleVin = sampleVin(realVin, secret);
                    }
                    parts[vinCol] = sampleVin;
                    line = String.join(",", parts);
                }
            }
            w.write(line);
            w.write('\n');
            if (bytes.size() > maxBytes) return false;
        }
        w.flush();
        return bytes.size() <= maxBytes;
    }

    private static String requestIdToZeros(String name) {
        Matcher m = REQUEST_ID.matcher(name);
        return m.find() ? m.replaceFirst(m.group(1) + "0".repeat(m.group(2).length())) : name;
    }

    private static String sampleVin(String vin, byte[] secret) {
        String wmi = vin.length() >= 3 ? vin.substring(0, 3) : "XPX";
        byte[] h = sha256(secret, vin);
        StringBuilder sb = new StringBuilder(wmi).append("ZZZSAMPLE");
        for (int i = 0; sb.length() < 17; i++) sb.append(VIN_CHARS.charAt((h[i] & 0xFF) % VIN_CHARS.length()));
        return sb.toString();
    }

    private static byte[] sha256(byte[] secret, String v) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(secret);
            return md.digest(v.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
