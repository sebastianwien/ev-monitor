package com.evmonitor.infrastructure.external.mistral;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

/**
 * Mistral AI (La Plateforme, EU) fuer den Sprachlog. Ohne {@code apiKey} ist das Feature aus:
 * die Clients werfen {@code NOT_CONFIGURED}, ohne einen Request zu senden.
 */
@ConfigurationProperties(prefix = "mistral")
@Component
@Getter
@Setter
public class MistralProperties {

    private static final BigDecimal SIXTY = BigDecimal.valueOf(60);
    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);

    private String apiKey = "";
    private String baseUrl = "https://api.mistral.ai";
    private String transcribeModel = "voxtral-mini-latest";
    private String extractModel = "voxtral-small-latest";
    /** Wartezeit vor dem ersten Retry nach 429, verdoppelt sich je Versuch. */
    private long rateLimitBackoffMs = 500;
    /** Preise je konfiguriertem Modellnamen, fuer {@code voice_draft.cost_usd}. */
    private Map<String, Price> pricing = new HashMap<>();

    /** Kosten eines Aufrufs; ohne Preis fuer das Modell 0 statt Fehler, die Nutzung zaehlt trotzdem. */
    public BigDecimal cost(String model, Double audioSeconds, Integer promptTokens, Integer completionTokens) {
        Price p = pricing.getOrDefault(model, new Price());
        BigDecimal audio = audioSeconds == null ? BigDecimal.ZERO
                : p.getUsdPerAudioMinute().multiply(BigDecimal.valueOf(audioSeconds)).divide(SIXTY, 6, RoundingMode.HALF_UP);
        BigDecimal in = perMillion(p.getUsdPerMillionInputTokens(), promptTokens);
        BigDecimal out = perMillion(p.getUsdPerMillionOutputTokens(), completionTokens);
        return audio.add(in).add(out).setScale(6, RoundingMode.HALF_UP);
    }

    private static BigDecimal perMillion(BigDecimal usd, Integer tokens) {
        if (tokens == null) return BigDecimal.ZERO;
        return usd.multiply(BigDecimal.valueOf(tokens)).divide(MILLION, 6, RoundingMode.HALF_UP);
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Getter
    @Setter
    public static class Price {
        private BigDecimal usdPerAudioMinute = BigDecimal.ZERO;
        private BigDecimal usdPerMillionInputTokens = BigDecimal.ZERO;
        private BigDecimal usdPerMillionOutputTokens = BigDecimal.ZERO;
    }
}
