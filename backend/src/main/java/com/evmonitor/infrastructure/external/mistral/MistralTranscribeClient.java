package com.evmonitor.infrastructure.external.mistral;

import com.evmonitor.application.voice.SpeechTranscriber;
import com.evmonitor.application.voice.Transcript;
import com.evmonitor.application.voice.VoiceProviderException;
import com.evmonitor.application.voice.VoiceProviderException.Reason;
import com.evmonitor.application.voice.VoiceUsage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * Stufe 1 des Sprachlogs: Voxtral Mini Transcribe mit Context Biasing.
 *
 * <p>Gemessen am 03.10.2026: Bias-Begriffe muessen als wiederholte Multipart-Felder kommen (ein
 * JSON-String wird still ignoriert), {@code language} wirkt nicht und wird darum nicht gesendet.
 */
@Component
public class MistralTranscribeClient implements SpeechTranscriber {

    private final RestTemplate restTemplate;
    private final MistralProperties props;
    private final ObjectMapper objectMapper;

    public MistralTranscribeClient(@Qualifier("mistralRestTemplate") RestTemplate restTemplate,
                                   MistralProperties props, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @Override
    public Transcript transcribe(byte[] audio, String mimeType, List<String> biasTerms) {
        HttpHeaders headers = MistralHttp.auth(props);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.parseMediaType(mimeType));
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("model", props.getTranscribeModel());
        form.add("file", new HttpEntity<>(new ByteArrayResource(audio) {
            @Override
            public String getFilename() {
                return "audio";
            }
        }, fileHeaders));
        biasTerms.forEach(t -> form.add("context_bias", t));

        String body = MistralHttp.call(() -> restTemplate.postForObject(
                props.getBaseUrl() + "/v1/audio/transcriptions", new HttpEntity<>(form, headers), String.class),
                props.getRateLimitBackoffMs());
        return parse(body);
    }

    private Transcript parse(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode text = root.path("text");
            if (!text.isTextual()) throw new VoiceProviderException(Reason.INVALID_RESPONSE, "Transkript ohne Text");
            JsonNode usage = root.path("usage");
            // Unterstrich-Phrasen landen woertlich im Transkript ("Aral_Pulse")
            String model = props.getTranscribeModel();
            Double seconds = usage.hasNonNull("prompt_audio_seconds") ? usage.get("prompt_audio_seconds").asDouble() : null;
            Integer in = intOrNull(usage, "prompt_tokens");
            Integer out = intOrNull(usage, "completion_tokens");
            return new Transcript(text.asText().replace('_', ' ').trim(),
                    new VoiceUsage(model, seconds, in, out, props.cost(model, seconds, in, out)));
        } catch (VoiceProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new VoiceProviderException(Reason.INVALID_RESPONSE, "Transkript nicht lesbar");
        }
    }

    static Integer intOrNull(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asInt() : null;
    }
}
