package com.evmonitor.infrastructure.web;

import com.evmonitor.application.InternalEvLogRequest;
import com.evmonitor.application.InternalTripRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Core-Seite des Vertrags mit dem Connectors-{@code CoreApiClient}. Die Datei
 * {@code contract/internal-api.json} liegt identisch in beiden Repos; Connectors prüft dort, dass
 * es genau diese Felder sendet, hier wird geprüft, dass der Core genau diese Felder liest. Ohne
 * den Test bliebe ein umbenanntes Feld auf beiden Seiten grün und käme still als null an, weil
 * Spring unbekannte Felder ignoriert.
 */
class InternalApiContractTest {

    /** Wie der Web-Mapper des Core, aber streng: ein Vertragsfeld ohne Gegenstück fällt auf. */
    private final ObjectMapper mapper = Jackson2ObjectMapperBuilder.json().failOnUnknownProperties(true).build();

    @Test
    void logsEndpointReadsExactlyTheContractFields() throws Exception {
        assertRecordReadsContract("POST /api/internal/logs", InternalEvLogRequest.class);
    }

    @Test
    void tripsEndpointReadsExactlyTheContractFields() throws Exception {
        assertRecordReadsContract("POST /api/internal/trips", InternalTripRequest.class);
    }

    @Test
    void eudaImportEndpointReadsExactlyTheContractParts() throws Exception {
        Method m = InternalEuDataActController.class.getMethod("importDataset",
                String.class, String.class, String.class, MultipartFile.class);
        Map<String, String> parts = new TreeMap<>();
        for (Parameter p : m.getParameters()) {
            RequestPart rp = p.getAnnotation(RequestPart.class);
            String name = !rp.value().isEmpty() ? rp.value() : rp.name();
            parts.put(name, MultipartFile.class.equals(p.getType()) ? "file" : "part");
        }

        assertThat(parts).isEqualTo(contractFields("POST /api/internal/eu-data-act/import"));
    }

    private void assertRecordReadsContract(String endpoint, Class<? extends Record> type) throws Exception {
        Map<String, String> fields = contractFields(endpoint);
        Set<String> components = Arrays.stream(type.getRecordComponents())
                .map(RecordComponent::getName).collect(Collectors.toSet());
        assertThat(components).as("Record-Komponenten von %s", type.getSimpleName())
                .containsExactlyInAnyOrderElementsOf(fields.keySet());

        Record parsed = mapper.treeToValue(sampleFor(fields), type);

        for (RecordComponent c : type.getRecordComponents()) {
            Object value = c.getAccessor().invoke(parsed);
            assertThat(value).as("%s.%s kommt an", type.getSimpleName(), c.getName()).isNotNull();
            if (value instanceof Boolean b) {
                assertThat(b).as("%s.%s kommt an", type.getSimpleName(), c.getName()).isTrue();
            }
        }
    }

    /** Ein Body, in dem jedes Vertragsfeld mit einem gültigen Wert seines Typs belegt ist. */
    private ObjectNode sampleFor(Map<String, String> fields) {
        ObjectNode node = mapper.createObjectNode();
        fields.forEach((name, t) -> {
            switch (t) {
                case "uuid" -> node.put(name, UUID.randomUUID().toString());
                case "number" -> node.put(name, 12.5);
                case "integer" -> node.put(name, 7);
                case "boolean" -> node.put(name, true);
                case "string" -> node.put(name, "x");
                case "local-date-time-array" -> node.putArray(name).add(2026).add(1).add(15).add(8).add(0);
                case "offset-date-time" -> node.put(name, "2026-01-15T08:00:00Z");
                default -> throw new IllegalArgumentException("Unbekannter Vertragstyp " + t + " für " + name);
            }
        });
        return node;
    }

    private Map<String, String> contractFields(String endpoint) throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/contract/internal-api.json")) {
            JsonNode fields = new ObjectMapper().readTree(in).path("endpoints").path(endpoint).path("fields");
            assertThat(fields.isObject()).as("Vertrag kennt %s", endpoint).isTrue();
            Map<String, String> result = new TreeMap<>();
            fields.properties().forEach(e -> result.put(e.getKey(), e.getValue().asText()));
            return result;
        }
    }
}
