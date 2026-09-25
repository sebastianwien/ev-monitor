package com.evmonitor.infrastructure.web;

import com.evmonitor.application.InternalEvLogRequest;
import com.evmonitor.application.InternalTripRequest;
import com.evmonitor.application.ingest.api.InternalIngestRequest;
import com.evmonitor.application.ingest.api.InternalIngestResponse;
import com.evmonitor.application.ingest.api.InternalIngestResponse.EntryResult;
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
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
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
        Method m = InternalVwEudaController.class.getMethod("importDataset",
                String.class, String.class, String.class, MultipartFile.class);
        Map<String, String> parts = new TreeMap<>();
        for (Parameter p : m.getParameters()) {
            RequestPart rp = p.getAnnotation(RequestPart.class);
            String name = !rp.value().isEmpty() ? rp.value() : rp.name();
            parts.put(name, MultipartFile.class.equals(p.getType()) ? "file" : "part");
        }

        assertThat(parts).isEqualTo(contractFields("POST /api/internal/eu-data-act/import"));
    }

    @Test
    void ingestEndpointReadsExactlyTheContractFields() throws Exception {
        assertRecordReadsContract("POST /api/internal/ingest", InternalIngestRequest.class);
    }

    @Test
    void ingestEndpointWritesExactlyTheContractResponse() throws Exception {
        EntryResult entry = new EntryResult(InternalIngestResponse.Status.CREATED, UUID.randomUUID());
        JsonNode written = mapper.valueToTree(new InternalIngestResponse(List.of(entry), List.of(entry)));

        assertJsonMatches("response", contract().path("endpoints").path("POST /api/internal/ingest").path("response"), written);
    }

    private void assertRecordReadsContract(String endpoint, Class<? extends Record> type) throws Exception {
        JsonNode fields = contract().path("endpoints").path(endpoint).path("fields");
        assertThat(fields.isObject()).as("Vertrag kennt %s", endpoint).isTrue();
        assertComponentsMatch(type, fields);

        Record parsed = mapper.treeToValue(sampleFor(type, fields), type);

        assertEveryComponentArrives(parsed);
    }

    /** Record-Komponenten gleich Vertragsfeldern, verschachtelte Objekte und Listen rekursiv. */
    private void assertComponentsMatch(Class<?> type, JsonNode fields) throws Exception {
        Set<String> components = Arrays.stream(type.getRecordComponents())
                .map(RecordComponent::getName).collect(Collectors.toSet());
        assertThat(components).as("Record-Komponenten von %s", type.getSimpleName())
                .containsExactlyInAnyOrderElementsOf(fieldNames(fields));
        for (RecordComponent c : type.getRecordComponents()) {
            JsonNode nested = nestedObject(fields.path(c.getName()).asText());
            if (nested != null) assertComponentsMatch(elementType(c), nested);
        }
    }

    private void assertEveryComponentArrives(Record parsed) throws Exception {
        Class<?> type = parsed.getClass();
        for (RecordComponent c : type.getRecordComponents()) {
            Object value = c.getAccessor().invoke(parsed);
            assertThat(value).as("%s.%s kommt an", type.getSimpleName(), c.getName()).isNotNull();
            if (value instanceof Boolean b) {
                assertThat(b).as("%s.%s kommt an", type.getSimpleName(), c.getName()).isTrue();
            }
            if (value instanceof List<?> list) {
                assertThat(list).as("%s.%s kommt an", type.getSimpleName(), c.getName()).hasSize(1);
                if (list.get(0) instanceof Record r) assertEveryComponentArrives(r);
            }
            if (value instanceof Record r) assertEveryComponentArrives(r);
        }
    }

    /** Ein Body, in dem jedes Vertragsfeld mit einem gültigen Wert seines Typs belegt ist. */
    private ObjectNode sampleFor(Class<?> type, JsonNode fields) throws Exception {
        ObjectNode node = mapper.createObjectNode();
        for (String name : fieldNames(fields)) {
            String t = fields.path(name).asText();
            RecordComponent component = component(type, name);
            JsonNode nested = nestedObject(t);
            if (t.startsWith("array:")) {
                node.putArray(name).add(sampleFor(elementType(component), nested));
                continue;
            }
            if (t.startsWith("object:")) {
                node.set(name, sampleFor(elementType(component), nested));
                continue;
            }
            switch (t) {
                case "uuid" -> node.put(name, UUID.randomUUID().toString());
                case "number" -> node.put(name, 12.5);
                case "integer" -> node.put(name, 7);
                case "boolean" -> node.put(name, true);
                case "string" -> node.put(name, component != null && component.getType().isEnum()
                        ? component.getType().getEnumConstants()[0].toString() : "x");
                case "local-date-time-array" -> node.putArray(name).add(2026).add(1).add(15).add(8).add(0);
                case "local-date-time" -> node.put(name, "2026-01-15T08:00:00");
                case "offset-date-time" -> node.put(name, "2026-01-15T08:00:00Z");
                default -> throw new IllegalArgumentException("Unbekannter Vertragstyp " + t + " für " + name);
            }
        }
        return node;
    }

    /** Geschriebenes JSON hat genau die Vertragsfelder, verschachtelt geprüft. */
    private void assertJsonMatches(String where, JsonNode fields, JsonNode written) throws Exception {
        assertThat(fields.isObject()).as("Vertrag kennt %s", where).isTrue();
        assertThat(fieldNames(written)).as(where).containsExactlyInAnyOrderElementsOf(fieldNames(fields));
        for (String name : fieldNames(fields)) {
            String t = fields.path(name).asText();
            JsonNode v = written.path(name);
            String at = where + "." + name + " (" + t + "): " + v;
            if (t.startsWith("array:")) {
                assertThat(v.isArray() && !v.isEmpty()).as(at).isTrue();
                assertJsonMatches(at, nestedObject(t), v.get(0));
                continue;
            }
            switch (t) {
                case "uuid" -> assertThat(UUID.fromString(v.textValue())).as(at).isNotNull();
                case "string" -> assertThat(v.isTextual()).as(at).isTrue();
                default -> throw new IllegalArgumentException("Antworttyp " + t + " hier nicht vorgesehen");
            }
        }
    }

    /** Die Objektdefinition hinter {@code array:<Name>} oder {@code object:<Name>}, sonst null. */
    private JsonNode nestedObject(String type) throws Exception {
        int colon = type.indexOf(':');
        if (colon < 0) return null;
        JsonNode def = contract().path("objects").path(type.substring(colon + 1));
        assertThat(def.isObject()).as("Vertrag definiert %s", type).isTrue();
        return def;
    }

    /** Typ einer Komponente, bei {@code List<X>} das X. */
    private static Class<?> elementType(RecordComponent c) {
        if (c.getGenericType() instanceof ParameterizedType p) {
            return (Class<?>) p.getActualTypeArguments()[0];
        }
        return c.getType();
    }

    private static RecordComponent component(Class<?> type, String name) {
        return Arrays.stream(type.getRecordComponents()).filter(c -> c.getName().equals(name)).findFirst().orElse(null);
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new TreeSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private JsonNode contract() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/contract/internal-api.json")) {
            return new ObjectMapper().readTree(in);
        }
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
