package com.evmonitor.infrastructure.external.anthropic;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.evmonitor.application.tariff.crawl.TariffExtractionException;
import com.evmonitor.application.tariff.crawl.TariffExtractor;
import com.evmonitor.infrastructure.external.tariff.TariffPrompt;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Tarif-Crawler, Stufe "lesen", mit Claude: Messages API mit JSON-Schema (structured outputs). Prompt,
 * Schema und Seitenblock aus {@link TariffPrompt}. Fehlertexte der API werden nicht geloggt, sie können
 * Request-Inhalte zitieren. Welches Sprachmodell liest, entscheidet {@code TariffExtractorConfig}.
 */
@Slf4j
public class AnthropicTariffExtractClient implements TariffExtractor, AutoCloseable {

    /** Die Antwort ist JSON mit höchstens ein paar Dutzend Tarifen; der Rest bleibt fürs adaptive Nachdenken (bei Haiku 5.5 an). */
    private static final long MAX_TOKENS = 16_000;

    private final AnthropicProperties props;
    private final ObjectMapper lenientReader;
    private final ObjectMapper objectMapper;
    /** null ohne Key: dann geht kein Request raus. */
    private final AnthropicClient client;

    public AnthropicTariffExtractClient(AnthropicProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.lenientReader = objectMapper.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.client = !props.isConfigured() ? null : AnthropicOkHttpClient.builder()
                .apiKey(props.getApiKey())
                .baseUrl(props.getBaseUrl())
                .maxRetries(props.getMaxRetries())
                .timeout(Duration.ofSeconds(props.getTimeoutSeconds()))
                .build();
    }

    @Override
    public boolean isAvailable() {
        return client != null;
    }

    @Override
    public Extraction extract(Request request) {
        if (client == null) throw new TariffExtractionException("Claude: kein ANTHROPIC_API_KEY");
        MessageCreateParams params = MessageCreateParams.builder()
                .model(props.getTariffModel())
                .maxTokens(MAX_TOKENS)
                .system(TariffPrompt.SYSTEM)
                .addUserMessage(TariffPrompt.userMessage(request, objectMapper))
                .outputConfig(OutputConfig.builder()
                        .effort(OutputConfig.Effort.MEDIUM)
                        .format(JsonOutputFormat.builder().schema(schema()).build())
                        .build())
                .build();
        String text;
        Usage usage;
        try {
            Message message = client.messages().create(params);
            StopReason stop = message.stopReason().orElse(null);
            if (StopReason.MAX_TOKENS.equals(stop)) throw new TariffExtractionException("Claude: Antwort abgeschnitten");
            if (StopReason.REFUSAL.equals(stop)) throw new TariffExtractionException("Claude: Anfrage abgelehnt");
            text = message.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(t -> t.text())
                    .collect(Collectors.joining());
            usage = new Usage(props.getTariffModel(), (int) message.usage().inputTokens(), (int) message.usage().outputTokens());
        } catch (RateLimitException e) {
            throw new TariffExtractionException("Claude: RATE_LIMITED");
        } catch (AnthropicServiceException e) {
            throw new TariffExtractionException("Claude: HTTP " + e.statusCode());
        } catch (AnthropicIoException e) {
            throw new TariffExtractionException("Claude: nicht erreichbar");
        } catch (AnthropicException e) {
            // Fehlertexte des SDK zitieren Teile der Antwort, darum ein fester Text.
            throw new TariffExtractionException("Claude: Antwort nicht lesbar");
        }
        return parse(text, usage);
    }

    @Override
    public void close() {
        if (client != null) client.close();
    }

    private Extraction parse(String text, Usage usage) {
        TariffPrompt.Answer answer;
        try {
            answer = lenientReader.readValue(text, TariffPrompt.Answer.class);
        } catch (Exception e) {
            throw new TariffExtractionException("Antwort des Sprachmodells nicht lesbar");
        }
        log.info("Tarif-Crawler Claude: {} Tarife, {} Token rein, {} raus",
                answer.tariffs() == null ? 0 : answer.tariffs().size(), usage.promptTokens(), usage.completionTokens());
        return new Extraction(answer.tariffs() == null ? List.of() : answer.tariffs(),
                answer.tiers() == null ? List.of() : answer.tiers(), usage);
    }

    private static JsonOutputFormat.Schema schema() {
        JsonOutputFormat.Schema.Builder b = JsonOutputFormat.Schema.builder();
        withoutTypeLists(TariffPrompt.schema()).forEach((k, v) -> b.putAdditionalProperty(k, JsonValue.from(v)));
        return b.build();
    }

    /**
     * Das gemeinsame Schema schreibt "kann null sein" als Typliste ({@code ["number", "null"]}); für Claude
     * wird daraus {@code anyOf} mit einem null-Zweig, ein erlaubtes null im enum wandert in diesen Zweig.
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> withoutTypeLists(Map<String, Object> node) {
        Map<String, Object> out = new LinkedHashMap<>();
        node.forEach((k, v) -> out.put(k, k.equals("enum") ? v : convert(v)));
        if (!(out.get("type") instanceof List<?> types) || !types.contains("null")) return out;
        Map<String, Object> value = new LinkedHashMap<>(out);
        value.put("type", types.stream().filter(t -> !"null".equals(t)).findFirst().orElseThrow());
        if (value.get("enum") instanceof List<?> allowed) {
            List<Object> withoutNull = new ArrayList<>(allowed);
            withoutNull.removeIf(java.util.Objects::isNull);
            value.put("enum", withoutNull);
        }
        return Map.of("anyOf", List.of(value, Map.of("type", "null")));
    }

    @SuppressWarnings("unchecked")
    private static Object convert(Object v) {
        if (v instanceof Map<?, ?> m) return withoutTypeLists((Map<String, Object>) m);
        if (v instanceof List<?> l) return l.stream().map(AnthropicTariffExtractClient::convert).toList();
        return v;
    }
}
