package com.evmonitor.infrastructure.external.mistral;

import com.evmonitor.application.voice.DraftFields;
import com.evmonitor.application.voice.Extraction;
import com.evmonitor.application.voice.ExtractionContext;
import com.evmonitor.application.voice.LogDraftExtractor;
import com.evmonitor.application.voice.VoiceProviderException;
import com.evmonitor.application.voice.VoiceProviderException.Reason;
import com.evmonitor.application.voice.VoiceUsage;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static com.evmonitor.infrastructure.external.mistral.MistralTranscribeClient.intOrNull;

/**
 * Stufe 2 des Sprachlogs: Chat Completions mit striktem JSON-Schema. Modell per Property,
 * entschieden am 03.10.2026 fuer {@code voxtral-small-latest} (Ministral 8B erfand Werte).
 */
@Component
public class MistralExtractClient implements LogDraftExtractor {

    private final RestTemplate restTemplate;
    private final MistralProperties props;
    private final ObjectMapper objectMapper;
    private final ObjectMapper lenientReader;

    public MistralExtractClient(@Qualifier("mistralRestTemplate") RestTemplate restTemplate,
                                MistralProperties props, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.props = props;
        this.objectMapper = objectMapper;
        this.lenientReader = objectMapper.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public Extraction extract(String transcript, ExtractionContext context) {
        HttpHeaders headers = MistralHttp.auth(props);
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = Map.of(
                "model", props.getExtractModel(),
                "temperature", 0,
                "messages", List.of(
                        Map.of("role", "system", "content", MistralExtractionPrompt.SYSTEM),
                        Map.of("role", "user", "content", "Kontext:\n" + json(context) + "\n\nTranskript:\n" + transcript)),
                "response_format", Map.of("type", "json_schema", "json_schema",
                        Map.of("name", "log_draft", "strict", true, "schema", MistralExtractionPrompt.schema())));

        String response = MistralHttp.call(() -> restTemplate.postForObject(
                props.getBaseUrl() + "/v1/chat/completions", new HttpEntity<>(body, headers), String.class),
                props.getRateLimitBackoffMs());
        return parse(response);
    }

    private Extraction parse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            String content = root.path("choices").path(0).path("message").path("content").asText();
            DraftFields fields = lenientReader.readValue(content, DraftFields.class);
            JsonNode usage = root.path("usage");
            String model = props.getExtractModel();
            Integer in = intOrNull(usage, "prompt_tokens");
            Integer out = intOrNull(usage, "completion_tokens");
            return new Extraction(fields, new VoiceUsage(model, null, in, out, props.cost(model, null, in, out)));
        } catch (Exception e) {
            throw new VoiceProviderException(Reason.INVALID_RESPONSE, "Extraktion nicht lesbar");
        }
    }

    private String json(ExtractionContext context) {
        try {
            return objectMapper.writeValueAsString(context);
        } catch (Exception e) {
            throw new IllegalStateException("Kontext nicht serialisierbar", e);
        }
    }
}
