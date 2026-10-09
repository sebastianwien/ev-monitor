package com.evmonitor.infrastructure.external.mistral;

import com.evmonitor.application.rating.ReviewModerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Trefferquote der Moderation gegen echte Mistral-Antworten. Nicht im Standardlauf (Tag mistral-live),
 * Start: {@code MISTRAL_API_KEY=... ./gradlew mistralLiveTest}. Ziel mindestens 16 von 18.
 */
@Tag("mistral-live")
class MistralModerationLiveTest {

    @Test
    void fixturesHitExpectedRecommendation() throws Exception {
        String key = System.getenv("MISTRAL_API_KEY");
        Assumptions.assumeTrue(key != null && !key.isBlank(), "MISTRAL_API_KEY fehlt");
        ObjectMapper mapper = new ObjectMapper();
        MistralProperties props = new MistralProperties();
        props.setApiKey(key);
        MistralModerationClient client = new MistralModerationClient(new RestTemplate(), props, mapper);

        JsonNode fixtures = mapper.readTree(getClass().getResourceAsStream("/rating-moderation/fixtures.json"));
        int hits = 0;
        for (JsonNode f : fixtures) {
            ReviewModerator.Result result = client.moderate(new ReviewModerator.Input(f.path("model").asText(),
                    "BERICHT".equals(f.path("kind").asText()), f.path("text").asText(), f.path("kmDriven").asInt(),
                    f.path("logCount").asInt(), new BigDecimal(f.path("consumption").asText())));
            String got = mapper.readTree(result.json()).path("recommendation").asText();
            boolean hit = got.equals(f.path("expected").asText());
            if (hit) hits++;
            System.out.printf("%-24s erwartet %-24s bekommen %-24s %s%n", f.path("id").asText(), f.path("expected").asText(), got, hit ? "ok" : "ABWEICHUNG");
        }
        System.out.printf("Trefferquote: %d von %d%n", hits, fixtures.size());
        assertThat(hits).isGreaterThanOrEqualTo(16);
    }
}
