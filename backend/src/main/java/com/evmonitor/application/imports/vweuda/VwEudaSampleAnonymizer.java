package com.evmonitor.application.imports.vweuda;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Pseudonymisiert eine VW-EU-Data-Act-Datei (Upload oder Historien-Export) fuer die Ablage als
 * Testgrundlage. Ersetzt VIN, Konto- und Eintrags-IDs, verschiebt Koordinaten und schwaerzt
 * Namen, Adressen und Mails. Messwerte und Zeitstempel bleiben unveraendert, damit der Parser
 * auf der Kopie exakt dasselbe erkennt wie auf dem Original.
 *
 * <p>Koordinaten werden um ein ganzzahliges Vielfaches der Geohash-5-Zellgroesse verschoben
 * (Breite bis +-0,5 Grad, Laenge rund -25 Grad). So bleibt "gleiche Zelle / andere Zelle" ab
 * Praezision 5 erhalten. Versatz und Schluessel werden je Datei zufaellig gezogen und nirgends
 * gespeichert. Das Ergebnis ist pseudonymisiert, nicht anonym (Zeit und Kilometerstand bleiben).
 *
 * <p>Streamt: Elemente von Arrays auf oberster Ebene werden einzeln als Baum gelesen, auch
 * 50-MB-Exporte brauchen daher nur Speicher fuer ein Element.
 */
public final class VwEudaSampleAnonymizer {

    public record Result(String fileName, byte[] zip) {}

    static final double GEOHASH5_CELL = 360.0 / (1 << 13);
    static final String REDACTED = "redacted";

    private static final Pattern VIN = Pattern.compile("(?<![A-Z0-9])([A-HJ-NPR-Z0-9]{3})[A-HJ-NPR-Z0-9]{14}(?![A-Z0-9])");
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern MAIL = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.]+");
    private static final Pattern COORDINATES = Pattern.compile("(-?\\d{1,3}\\.(\\d+))\\s*,\\s*(-?\\d{1,3}\\.(\\d+))");
    private static final Pattern LOCATION_FIELD = Pattern.compile("(?i).*(location|position|coordinate|gps).*");
    private static final Pattern PERSONAL_FIELD = Pattern.compile(
            "(?i).*(address|street|city|zip|postal|name|mail|phone|plate|licen[cs]e).*");
    private static final Set<String> ID_FIELDS = Set.of("vin", "user_id", "userid");
    private static final String VIN_CHARS = "ABCDEFGHJKLMNPRSTUVWXYZ0123456789";

    private final ObjectMapper mapper;

    public VwEudaSampleAnonymizer(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** {@code json} ist das bereits entpackte JSON, {@code originalFilename} der Name der hochgeladenen Datei. */
    public Result anonymize(InputStream json, String originalFilename) throws IOException {
        Session s = new Session();
        String fileName = s.text(originalFilename == null ? "upload.json" : originalFilename);
        String base = fileName.replaceAll("(?i)\\.zip$", "");
        String entryName = base.toLowerCase(java.util.Locale.ROOT).endsWith(".json") ? base : base + ".json";

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes);
             JsonParser in = mapper.getFactory().createParser(json)) {
            zip.putNextEntry(new ZipEntry(entryName));
            try (JsonGenerator out = mapper.getFactory().createGenerator(zip)
                    .disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET)) {
                copyTopLevel(in, out, s);
            }
            zip.closeEntry();
        }
        return new Result(fileName, bytes.toByteArray());
    }

    private void copyTopLevel(JsonParser in, JsonGenerator out, Session s) throws IOException {
        JsonToken t = in.nextToken();
        if (t == JsonToken.START_ARRAY) {
            copyArrayElementwise(in, out, s, null);
        } else if (t == JsonToken.START_OBJECT) {
            out.writeStartObject();
            while (in.nextToken() == JsonToken.FIELD_NAME) {
                String field = in.currentName();
                out.writeFieldName(field);
                if (in.nextToken() == JsonToken.START_ARRAY) {
                    copyArrayElementwise(in, out, s, field);
                } else {
                    mapper.writeTree(out, s.value(field, mapper.readTree(in)));
                }
            }
            out.writeEndObject();
        } else if (t != null) {
            mapper.writeTree(out, s.value(null, mapper.readTree(in)));
        }
    }

    private void copyArrayElementwise(JsonParser in, JsonGenerator out, Session s, String field) throws IOException {
        out.writeStartArray();
        while (in.nextToken() != JsonToken.END_ARRAY) {
            mapper.writeTree(out, s.value(field, mapper.readTree(in)));
        }
        out.writeEndArray();
    }

    /** Zustand einer Datei: zufaelliger Schluessel und Versatz, nur im Speicher. */
    private static final class Session {
        private final byte[] secret = new byte[16];
        private final double dLat;
        private final double dLon;

        Session() {
            SecureRandom rnd = new SecureRandom();
            rnd.nextBytes(secret);
            dLat = (rnd.nextInt(23) - 11) * GEOHASH5_CELL;
            dLon = -(560 + rnd.nextInt(21)) * GEOHASH5_CELL;
        }

        /** Wert unter {@code field} (Objektfeld oder dataFieldName eines Eintrags) umschreiben. */
        JsonNode value(String field, JsonNode node) {
            if (node == null) return null;
            if (node.isObject()) return object((ObjectNode) node);
            if (node.isArray()) {
                ArrayNode a = (ArrayNode) node;
                for (int i = 0; i < a.size(); i++) a.set(i, value(field, a.get(i)));
                return a;
            }
            if (!node.isTextual()) return node;
            String v = node.asText();
            if (field != null) {
                if (ID_FIELDS.contains(field.toLowerCase(java.util.Locale.ROOT))) return TextNode.valueOf(id(v));
                if (LOCATION_FIELD.matcher(field).matches()) return TextNode.valueOf(shift(v));
                if (PERSONAL_FIELD.matcher(field).matches() && !"dataFieldName".equals(field)) return TextNode.valueOf(REDACTED);
            }
            return TextNode.valueOf(text(v));
        }

        private JsonNode object(ObjectNode o) {
            // Eintrag {dataFieldName, value}: die Bedeutung steht im dataFieldName, nicht im Feldnamen "value"
            String entryField = o.hasNonNull("dataFieldName") ? o.get("dataFieldName").asText() : null;
            if (o.has("latitude") && o.has("longitude") && o.get("latitude").isNumber() && o.get("longitude").isNumber()) {
                o.put("latitude", round(o.get("latitude").asDouble() + dLat, o.get("latitude").asText()));
                o.put("longitude", round(o.get("longitude").asDouble() + dLon, o.get("longitude").asText()));
            }
            Iterator<Map.Entry<String, JsonNode>> it = o.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                String key = e.getKey();
                if ("dataFieldName".equals(key) || "latitude".equals(key) || "longitude".equals(key)) continue;
                String context = "value".equals(key) && entryField != null ? entryField : key;
                e.setValue(value(context, e.getValue()));
            }
            return o;
        }

        /** Freitext: VINs, UUIDs und Mails ersetzen, alles andere unveraendert. */
        String text(String v) {
            if (v.isEmpty()) return v;
            String r = replace(VIN, v, m -> containsLetterAndDigit(m.group()) ? vin(m.group(), m.group(1)) : m.group());
            r = replace(UUID_PATTERN, r, m -> id(m.group()));
            return replace(MAIL, r, m -> REDACTED);
        }

        private String id(String v) {
            if (UUID_PATTERN.matcher(v).matches()) return UUID.nameUUIDFromBytes(hash(v)).toString();
            if (VIN.matcher(v).matches()) return vin(v, v.substring(0, 3));
            return v.isBlank() ? v : REDACTED;
        }

        private String vin(String original, String wmi) {
            byte[] h = hash(original);
            StringBuilder sb = new StringBuilder(wmi).append("ZZZSAMPLE");
            for (int i = 0; sb.length() < 17; i++) sb.append(VIN_CHARS.charAt((h[i] & 0xFF) % VIN_CHARS.length()));
            return sb.toString();
        }

        private String shift(String v) {
            Matcher m = COORDINATES.matcher(v);
            if (!m.find()) return text(v);
            String lat = String.format(java.util.Locale.ROOT, "%." + m.group(2).length() + "f", Double.parseDouble(m.group(1)) + dLat);
            String lon = String.format(java.util.Locale.ROOT, "%." + m.group(4).length() + "f", Double.parseDouble(m.group(3)) + dLon);
            return v.substring(0, m.start()) + lat + "," + lon + v.substring(m.end());
        }

        private double round(double value, String original) {
            int dot = original.indexOf('.');
            int decimals = dot < 0 ? 7 : Math.max(original.length() - dot - 1, 1);
            double f = Math.pow(10, Math.min(decimals, 9));
            return Math.round(value * f) / f;
        }

        private byte[] hash(String v) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                md.update(secret);
                return md.digest(v.getBytes(StandardCharsets.UTF_8));
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private static boolean containsLetterAndDigit(String s) {
        return s.chars().anyMatch(Character::isLetter) && s.chars().anyMatch(Character::isDigit);
    }

    private static String replace(Pattern p, String v, java.util.function.Function<Matcher, String> f) {
        Matcher m = p.matcher(v);
        if (!m.find()) return v;
        StringBuilder sb = new StringBuilder();
        do { m.appendReplacement(sb, Matcher.quoteReplacement(f.apply(m))); } while (m.find());
        m.appendTail(sb);
        return sb.toString();
    }

}
