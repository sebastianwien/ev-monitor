package com.evmonitor.infrastructure.external.mistral;

import com.evmonitor.application.rating.ReviewModerator;
import com.evmonitor.application.voice.VoiceProviderException;
import com.evmonitor.application.voice.VoiceProviderException.Reason;
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

/** Moderationsvorschlag für Erfahrungstexte: Chat Completions, temperature 0, striktes JSON-Schema. */
@Component
public class MistralModerationClient implements ReviewModerator {

    private final RestTemplate restTemplate;
    private final MistralProperties props;
    private final ObjectMapper objectMapper;

    public MistralModerationClient(@Qualifier("mistralRestTemplate") RestTemplate restTemplate,
                                   MistralProperties props, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @Override
    public Result moderate(Input input) {
        HttpHeaders headers = MistralHttp.auth(props);
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = Map.of(
                "model", props.getModerationModel(),
                "temperature", 0,
                "messages", List.of(
                        Map.of("role", "system", "content", MistralModerationPrompt.SYSTEM),
                        Map.of("role", "user", "content", userMessage(input))),
                "response_format", Map.of("type", "json_schema", "json_schema",
                        Map.of("name", "review_moderation", "strict", true, "schema", MistralModerationPrompt.schema())));

        String response = MistralHttp.call(() -> restTemplate.postForObject(
                props.getBaseUrl() + "/v1/chat/completions", new HttpEntity<>(body, headers), String.class),
                props.getRateLimitBackoffMs());
        return parse(response);
    }

    /** Messwerte vor dem Text, der Text selbst nur zwischen den Tags - alles darin ist Inhalt, keine Anweisung. */
    static String userMessage(Input input) {
        return "Modell: " + input.modelName() + "\n"
                + "Textart: " + (input.report() ? "BERICHT" : "KURZ") + "\n"
                + "Messdaten: " + value(input.kmDriven(), " km gefahren") + ", " + value(input.logCount(), " Ladevorgänge")
                + ", Verbrauch " + value(input.consumptionKwhPer100km(), " kWh/100 km") + "\n\n"
                + "<text>\n" + input.text().replace("</text>", "&lt;/text&gt;") + "\n</text>";
    }

    private static String value(Object v, String unit) {
        return v == null ? "unbekannt" : v + unit;
    }

    private Result parse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            String content = root.path("choices").path(0).path("message").path("content").asText();
            JsonNode result = objectMapper.readTree(content);
            if (!result.hasNonNull("recommendation")) throw new IllegalStateException("ohne Empfehlung");
            JsonNode usage = root.path("usage");
            String model = props.getModerationModel();
            Integer in = intOrNull(usage, "prompt_tokens");
            Integer out = intOrNull(usage, "completion_tokens");
            return new Result(objectMapper.writeValueAsString(result), model, in, out, props.cost(model, null, in, out));
        } catch (Exception e) {
            throw new VoiceProviderException(Reason.INVALID_RESPONSE, "Moderation nicht lesbar");
        }
    }
}
