package com.evmonitor.infrastructure.external.mistral;

import com.evmonitor.application.voice.Extraction;
import com.evmonitor.application.voice.ExtractionContext;
import com.evmonitor.application.voice.VoiceProviderException;
import com.evmonitor.domain.ChargingType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MistralExtractClientTest {

    static final String URL = "https://api.mistral.ai/v1/chat/completions";
    static final ObjectMapper MAPPER = new ObjectMapper();

    private MockRestServiceServer server;
    private MistralExtractClient client;

    private final ExtractionContext context = new ExtractionContext("2026-10-03 (Samstag), Zeitzone Europe/Berlin",
            "Nutzbare Akkukapazität 77 kWh", 48_000,
            List.of(new ExtractionContext.Candidate(0, "home", "Zuhause"), new ExtractionContext.Candidate(1, "station", "EnBW")),
            List.of(new ExtractionContext.Tariff(0, "EnBW mobility+")));

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        MistralProperties props = new MistralProperties();
        props.setApiKey("test-key");
        MistralProperties.Price price = new MistralProperties.Price();
        price.setUsdPerMillionInputTokens(new java.math.BigDecimal("0.10"));
        price.setUsdPerMillionOutputTokens(new java.math.BigDecimal("0.30"));
        props.getPricing().put("voxtral-small-latest", price);
        props.setRateLimitBackoffMs(0);
        client = new MistralExtractClient(restTemplate, props, MAPPER);
    }

    /** Antwortform wie die echte vom 03.10.2026, Inhalt ist das Feld-JSON als String. */
    static String response(String content) throws Exception {
        return """
                {"id":"x","model":"voxtral-small-latest","object":"chat.completion",
                 "usage":{"prompt_tokens":1450,"total_tokens":1530,"completion_tokens":80},
                 "choices":[{"index":0,"finish_reason":"stop","message":{"role":"assistant","content":%s}}]}"""
                .formatted(MAPPER.writeValueAsString(content));
    }

    @Test
    void sendsStrictSchemaPromptContextAndTranscript() throws Exception {
        server.expect(requestTo(URL))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(request -> {
                    JsonNode body = MAPPER.readTree(((MockClientHttpRequest) request).getBodyAsString());
                    assertThat(body.path("model").asText()).isEqualTo("voxtral-small-latest");
                    assertThat(body.path("temperature").asInt()).isZero();
                    JsonNode format = body.path("response_format");
                    assertThat(format.path("type").asText()).isEqualTo("json_schema");
                    assertThat(format.path("json_schema").path("strict").asBoolean()).isTrue();
                    JsonNode schema = format.path("json_schema").path("schema");
                    assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
                    assertThat(schema.path("required")).hasSize(19);
                    assertThat(schema.path("properties").has("spokenAddress")).isTrue();
                    assertThat(body.path("messages").get(0).path("content").asText()).contains("Nie raten");
                    String user = body.path("messages").get(1).path("content").asText();
                    assertThat(user).contains("\"letzterTachostand\":48000", "\"ortKandidaten\"", "\"tarife\"",
                            "Transkript:\nGeladen bei EnBW");
                })
                .andRespond(withSuccess(response("{\"uncertain\":[]}"), MediaType.APPLICATION_JSON));

        client.extract("Geladen bei EnBW", context);

        server.verify();
    }

    @Test
    void parsesFieldsAndUsage() throws Exception {
        server.expect(requestTo(URL)).andRespond(withSuccess(response("""
                {"placeIndex":1,"placeKind":"station","spokenOperator":null,"spokenAddress":"Lindenweg 4, Bamberg","tariffIndex":0,"kwhCharged":32,
                 "kwhAtVehicle":null,"socBefore":60,"socAfter":95,"odometerKm":48210,"costEur":18.4,"pricePerKwh":null,
                 "loggedAt":null,"chargeDurationMinutes":null,"maxChargingPowerKw":null,"chargingType":"DC",
                 "routeType":null,"tireType":"WINTER","uncertain":["tariffIndex"]}"""), MediaType.APPLICATION_JSON));

        Extraction e = client.extract("...", context);

        assertThat(e.fields().placeIndex()).isEqualTo(1);
        assertThat(e.fields().spokenAddress()).isEqualTo("Lindenweg 4, Bamberg");
        assertThat(e.fields().kwhCharged()).isEqualTo(32.0);
        assertThat(e.fields().odometerKm()).isEqualTo(48_210);
        assertThat(e.fields().chargingType()).isEqualTo(ChargingType.DC);
        assertThat(e.fields().tireType()).hasToString("WINTER");
        assertThat(e.fields().uncertain()).containsExactly("tariffIndex");
        assertThat(e.usage().model()).isEqualTo("voxtral-small-latest");
        assertThat(e.usage().promptTokens()).isEqualTo(1450);
        assertThat(e.usage().completionTokens()).isEqualTo(80);
        assertThat(e.usage().audioSeconds()).isNull();
        // 1450 x 0,10 / 1 Mio + 80 x 0,30 / 1 Mio
        assertThat(e.usage().costUsd()).isEqualByComparingTo("0.000169");
    }

    @Test
    void unparseableContentIsAnInvalidResponse() throws Exception {
        server.expect(requestTo(URL)).andRespond(withSuccess(response("kein json"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.extract("...", context))
                .isInstanceOfSatisfying(VoiceProviderException.class,
                        e -> assertThat(e.reason()).isEqualTo(VoiceProviderException.Reason.INVALID_RESPONSE));
    }

    @Test
    void clientErrorIsNotRetried() {
        server.expect(ExpectedCount.once(), requestTo(URL)).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> client.extract("...", context))
                .isInstanceOfSatisfying(VoiceProviderException.class,
                        e -> assertThat(e.reason()).isEqualTo(VoiceProviderException.Reason.REJECTED));
        server.verify();
    }
}
