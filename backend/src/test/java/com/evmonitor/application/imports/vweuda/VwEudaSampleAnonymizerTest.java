package com.evmonitor.application.imports.vweuda;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class VwEudaSampleAnonymizerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final VwEudaSampleAnonymizer anonymizer = new VwEudaSampleAnonymizer(mapper);
    private final VwEudaJsonParser parser = new VwEudaJsonParser(mapper);

    @Test
    void replacesVinAccountIdsAndEntryKeys_butKeepsValuesAndTimestamps() throws Exception {
        byte[] original = resource("eudataact/telemetry_only_15min_drop.json");

        String out = anonymizeToString(original, "TMBTEST0000000001_20260918133730.json");

        assertThat(out).doesNotContain("TMBTEST0000000001")
                .doesNotContain("00000000-0000-0000-0000-000000000001")
                .doesNotContain("62a0652d-2dae-32af-9404-4879cf6f669a")
                .contains("\"2026-09-18T13:37:30.669Z\"")
                .contains("\"15.5 kWh/100km\"");
        JsonNode root = mapper.readTree(out);
        assertThat(root.get("vin").asText()).startsWith("TMB").hasSize(17);
    }

    @Test
    void shiftsLocations_byWholeGeohashCells_andNeverKeepsTheOriginal() throws Exception {
        byte[] original = resource("eudataact/telemetry_only_15min_drop.json");

        JsonNode root = mapper.readTree(anonymizeToString(original, "drop.json"));

        String location = null;
        for (JsonNode e : root.get("Data")) {
            if ("persLocation".equals(e.get("dataFieldName").asText())) location = e.get("value").asText();
        }
        assertThat(location).isNotNull().isNotEqualTo("[52.000000,13.000000]");
        String[] parts = location.replaceAll("[\\[\\]]", "").split(",");
        double lat = Double.parseDouble(parts[0]);
        double lon = Double.parseDouble(parts[1]);
        double cell = 360.0 / (1 << 13);
        long cells = Math.round((lat - 52.0) / cell);
        assertThat(lat - 52.0 - cells * cell).isCloseTo(0.0, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(lat).isBetween(51.4, 52.6);
        assertThat(lon).isLessThan(-10.0);
        assertThat(parts[1]).matches("-?\\d+\\.\\d{6}");
    }

    @Test
    void redactsNamesAddressesAndMails() throws Exception {
        String json = """
                {"vin":"WVWZZZE1ZPP012345","Data":[
                  {"key":"x","dataFieldName":"chargingStationAddress","value":"Hauptstr. 1, 12345 Berlin"},
                  {"key":"y","dataFieldName":"ownerName","value":"Erika Mustermann"},
                  {"key":"z","dataFieldName":"note","value":"mail an erika@example.com"},
                  {"key":"w","dataFieldName":"soc","value":"80"}]}
                """;

        String out = anonymizeToString(json.getBytes(StandardCharsets.UTF_8), "x.json");

        assertThat(out).doesNotContain("Hauptstr").doesNotContain("Mustermann").doesNotContain("erika@example.com")
                .doesNotContain("WVWZZZE1ZPP012345").contains("\"80\"");
    }

    @Test
    void mapsTheSameIdentifierConsistentlyWithinOneFile() throws Exception {
        String json = """
                {"Data":[{"dataFieldName":"tripId","value":"a8239b83-595f-4f86-b91b-fa9d0065b980"},
                         {"dataFieldName":"tripId","value":"a8239b83-595f-4f86-b91b-fa9d0065b980"}]}
                """;

        JsonNode root = mapper.readTree(anonymizeToString(json.getBytes(StandardCharsets.UTF_8), "x.json"));

        String first = root.get("Data").get(0).get("value").asText();
        assertThat(first).isNotEqualTo("a8239b83-595f-4f86-b91b-fa9d0065b980").hasSize(36);
        assertThat(root.get("Data").get(1).get("value").asText()).isEqualTo(first);
    }

    @Test
    void replacesTheVinInTheFileName() throws Exception {
        VwEudaSampleAnonymizer.Result r = anonymizer.anonymize(
                new ByteArrayInputStream("{\"Data\":[]}".getBytes(StandardCharsets.UTF_8)),
                "WVWZZZE1ZPP012345_20260918133730.zip");

        assertThat(r.fileName()).startsWith("WVW").doesNotContain("WVWZZZE1ZPP012345").endsWith("_20260918133730.zip");
        assertThat(onlyEntryName(r.zip())).endsWith("_20260918133730.json").doesNotContain("WVWZZZE1ZPP012345");
    }

    @Test
    void parserFindsTheSameSessions_inTheUploadFormat() throws Exception {
        byte[] original = resource("eudataact/WVWZZZ-ID7_20251213015510.json");

        assertSameSessions(original);
    }

    @Test
    void parserFindsTheSameSessions_inTheMebSignalIdFormat() throws Exception {
        byte[] original = unzipOnlyJson(resource("eudataact/MEB_signal_ids.zip"));

        assertSameSessions(original);
    }

    private void assertSameSessions(byte[] original) throws IOException {
        byte[] anonymized = unzipOnlyJson(anonymizer.anonymize(new ByteArrayInputStream(original), "x.json").zip());

        VwEudaParseResult before = parser.parse(new ByteArrayResource(original), false);
        VwEudaParseResult after = parser.parse(new ByteArrayResource(anonymized), false);

        assertThat(before.sessions()).isNotEmpty();
        assertThat(after.sessions()).isEqualTo(before.sessions());
    }

    private String anonymizeToString(byte[] json, String name) throws IOException {
        return new String(unzipOnlyJson(anonymizer.anonymize(new ByteArrayInputStream(json), name).zip()), StandardCharsets.UTF_8);
    }

    private static byte[] unzipOnlyJson(byte[] zip) throws IOException {
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry e; (e = z.getNextEntry()) != null; ) if (e.getName().endsWith(".json")) return z.readAllBytes();
        }
        throw new IllegalStateException("kein JSON im ZIP");
    }

    private static String onlyEntryName(byte[] zip) throws IOException {
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(zip))) {
            return z.getNextEntry().getName();
        }
    }

    private byte[] resource(String path) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
            return in.readAllBytes();
        }
    }
}
