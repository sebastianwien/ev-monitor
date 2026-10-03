package com.evmonitor.infrastructure.external.mistral;

import com.evmonitor.application.voice.Transcript;
import com.evmonitor.application.voice.VoiceProviderException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MistralTranscribeClientTest {

    static final String URL = "https://api.mistral.ai/v1/audio/transcriptions";
    /** Echte Antwort vom 03.10.2026 (synthetische Stimme). */
    static final String RESPONSE = """
            {"model":"voxtral-mini-latest","text":"Geladen bei EnBW, 32 Kilowattstunden, Tacho 48.210, von 60 auf 95%, 18,40 Euro.",
             "language":null,"segments":[],"usage":{"prompt_audio_seconds":11,"prompt_tokens":9,"total_tokens":428,
             "completion_tokens":44,"prompt_tokens_details":{"cached_tokens":0,"audio_tokens":375},"service_tier":"standard"},
             "finish_reason":null}""";

    private MockRestServiceServer server;
    private MistralTranscribeClient client;
    private MistralProperties props;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        props = new MistralProperties();
        props.setApiKey("test-key");
        props.setRateLimitBackoffMs(0);
        client = new MistralTranscribeClient(restTemplate, props, new ObjectMapper());
    }

    @Test
    void sendsAudioModelAndBiasAsRepeatedMultipartFields() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(request -> {
                    String body = ((MockClientHttpRequest) request).getBodyAsString();
                    assertThat(body).contains("name=\"model\"", "voxtral-mini-latest", "name=\"file\"", "Content-Type: audio/webm");
                    // Ein JSON-Array als String ignoriert Mistral still - jeder Begriff ist ein eigenes Feld
                    assertThat(body.split("name=\"context_bias\"", -1)).hasSize(3);
                    assertThat(body).doesNotContain("[\"EnBW\"");
                })
                .andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON));

        client.transcribe(new byte[]{1, 2, 3}, "audio/webm", List.of("EnBW", "Tacho"));

        server.verify();
    }

    @Test
    void parsesTextAndUsage() {
        server.expect(requestTo(URL)).andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON));

        Transcript t = client.transcribe(new byte[]{1}, "audio/mp4", List.of());

        assertThat(t.text()).startsWith("Geladen bei EnBW, 32 Kilowattstunden");
        assertThat(t.usage().model()).isEqualTo("voxtral-mini-latest");
        assertThat(t.usage().audioSeconds()).isEqualTo(11.0);
        assertThat(t.usage().promptTokens()).isEqualTo(9);
        assertThat(t.usage().completionTokens()).isEqualTo(44);
    }

    @Test
    void costIsFrozenFromConfiguredPricePerAudioMinute() {
        MistralProperties.Price price = new MistralProperties.Price();
        price.setUsdPerAudioMinute(new java.math.BigDecimal("0.003"));
        props.getPricing().put("voxtral-mini-latest", price);
        server.expect(requestTo(URL)).andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON));

        Transcript t = client.transcribe(new byte[]{1}, "audio/mp4", List.of());

        assertThat(t.usage().costUsd()).isEqualByComparingTo("0.000550");
    }

    @Test
    void unknownModelPriceCostsZeroInsteadOfFailing() {
        server.expect(requestTo(URL)).andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON));

        assertThat(client.transcribe(new byte[]{1}, "audio/mp4", List.of()).usage().costUsd()).isEqualByComparingTo("0");
    }

    @Test
    void underscorePhrasesBecomeSpaces() {
        server.expect(requestTo(URL)).andRespond(withSuccess(
                "{\"text\":\"Geladen bei Aral_Pulse\",\"usage\":{\"prompt_audio_seconds\":3}}", MediaType.APPLICATION_JSON));

        assertThat(client.transcribe(new byte[]{1}, "audio/mp4", List.of()).text()).isEqualTo("Geladen bei Aral Pulse");
    }

    @Test
    void clientErrorIsNotRetried() {
        server.expect(ExpectedCount.once(), requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"message\":\"Context bias item 'Aral Pulse' must not contain commas or whitespace\",\"code\":\"3051\"}"));

        assertThatThrownBy(() -> client.transcribe(new byte[]{1}, "audio/mp4", List.of("x")))
                .isInstanceOfSatisfying(VoiceProviderException.class,
                        e -> assertThat(e.reason()).isEqualTo(VoiceProviderException.Reason.REJECTED));
        server.verify();
    }

    @Test
    void rateLimitIsRetriedWithBackoff() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo(URL)).andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON));

        assertThat(client.transcribe(new byte[]{1}, "audio/mp4", List.of()).text()).isNotBlank();
        server.verify();
    }

    @Test
    void persistentRateLimitGivesUp() {
        server.expect(ExpectedCount.times(3), requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client.transcribe(new byte[]{1}, "audio/mp4", List.of()))
                .isInstanceOfSatisfying(VoiceProviderException.class,
                        e -> assertThat(e.reason()).isEqualTo(VoiceProviderException.Reason.RATE_LIMITED));
        server.verify();
    }

    @Test
    void withoutKeyNothingIsSent() {
        props.setApiKey("");

        assertThatThrownBy(() -> client.transcribe(new byte[]{1}, "audio/mp4", List.of()))
                .isInstanceOfSatisfying(VoiceProviderException.class,
                        e -> assertThat(e.reason()).isEqualTo(VoiceProviderException.Reason.NOT_CONFIGURED));
        server.verify();
    }
}
