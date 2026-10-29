package com.evmonitor.infrastructure.external.anthropic;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Claude (Anthropic) für den Tarif-Crawler. Ohne {@code apiKey} liest der Crawler mit Mistral
 * ({@code TariffExtractorConfig}).
 */
@ConfigurationProperties(prefix = "anthropic")
@Component
@Getter
@Setter
public class AnthropicProperties {

    private String apiKey = "";
    private String baseUrl = "https://api.anthropic.com";
    /** Liest Tarife aus Preisseiten, strukturierte Ausgabe. */
    private String tariffModel = "claude-haiku-5-5";
    /** Wiederholungen des SDK bei 429, 5xx und Verbindungsfehlern. */
    private int maxRetries = 2;
    private int timeoutSeconds = 120;

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
