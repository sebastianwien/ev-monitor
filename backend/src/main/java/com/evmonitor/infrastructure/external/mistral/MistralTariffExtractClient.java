package com.evmonitor.infrastructure.external.mistral;

import com.evmonitor.application.tariff.crawl.TariffExtractionException;
import com.evmonitor.application.tariff.crawl.TariffExtractor;
import com.evmonitor.application.voice.VoiceProviderException;
import com.evmonitor.infrastructure.external.tariff.TariffPrompt;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static com.evmonitor.infrastructure.external.mistral.MistralTranscribeClient.intOrNull;

/**
 * Tarif-Crawler, Stufe "lesen", mit Mistral: Chat Completions mit striktem JSON-Schema. Prompt, Schema
 * und Seitenblock aus {@link TariffPrompt}. Fehlertexte von Mistral werden nicht geloggt. Welches
 * Sprachmodell liest, entscheidet {@code TariffExtractorConfig}.
 */
@Slf4j
public class MistralTariffExtractClient implements TariffExtractor {

    private final RestTemplate restTemplate;
    private final MistralProperties props;
    private final ObjectMapper objectMapper;
    private final ObjectMapper lenientReader;

    public MistralTariffExtractClient(RestTemplate restTemplate,
                                      MistralProperties props, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.props = props;
        this.objectMapper = objectMapper;
        this.lenientReader = objectMapper.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public boolean isAvailable() {
        return props.isConfigured();
    }

    @Override
    public Extraction extract(Request request) {
        String response;
        try {
            HttpHeaders headers = MistralHttp.auth(props);
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> body = Map.of(
                    "model", props.getTariffModel(),
                    "temperature", 0,
                    "messages", List.of(
                            Map.of("role", "system", "content", TariffPrompt.SYSTEM),
                            Map.of("role", "user", "content", TariffPrompt.userMessage(request, objectMapper))),
                    "response_format", Map.of("type", "json_schema", "json_schema",
                            Map.of("name", "tariffs", "strict", true, "schema", TariffPrompt.schema())));
            response = MistralHttp.call(() -> restTemplate.postForObject(
                    props.getBaseUrl() + "/v1/chat/completions", new HttpEntity<>(body, headers), String.class),
                    props.getRateLimitBackoffMs());
        } catch (VoiceProviderException e) {
            throw new TariffExtractionException("Mistral: " + e.reason());
        }
        return parse(response);
    }


    private Extraction parse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            String content = root.path("choices").path(0).path("message").path("content").asText();
            TariffPrompt.Answer answer = lenientReader.readValue(content, TariffPrompt.Answer.class);
            JsonNode usage = root.path("usage");
            Usage u = new Usage(props.getTariffModel(), intOrNull(usage, "prompt_tokens"), intOrNull(usage, "completion_tokens"));
            log.info("Tarif-Crawler Mistral: {} Tarife, {} Token rein, {} raus",
                    answer.tariffs() == null ? 0 : answer.tariffs().size(), u.promptTokens(), u.completionTokens());
            return new Extraction(answer.tariffs() == null ? List.of() : answer.tariffs(),
                    answer.tiers() == null ? List.of() : answer.tiers(), u);
        } catch (Exception e) {
            throw new TariffExtractionException("Antwort des Sprachmodells nicht lesbar");
        }
    }
}
