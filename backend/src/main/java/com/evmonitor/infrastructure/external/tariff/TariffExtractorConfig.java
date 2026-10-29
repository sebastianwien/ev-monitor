package com.evmonitor.infrastructure.external.tariff;

import com.evmonitor.application.tariff.crawl.TariffExtractor;
import com.evmonitor.infrastructure.external.anthropic.AnthropicProperties;
import com.evmonitor.infrastructure.external.anthropic.AnthropicTariffExtractClient;
import com.evmonitor.infrastructure.external.mistral.MistralProperties;
import com.evmonitor.infrastructure.external.mistral.MistralTariffExtractClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/** Wer im Tarif-Crawler die Seiten liest: Claude, sobald {@code ANTHROPIC_API_KEY} gesetzt ist, sonst Mistral. */
@Configuration
@Slf4j
public class TariffExtractorConfig {

    @Bean
    public TariffExtractor tariffExtractor(AnthropicProperties anthropic, MistralProperties mistral,
                                           @Qualifier("mistralTariffRestTemplate") RestTemplate mistralTariffRestTemplate,
                                           ObjectMapper objectMapper) {
        if (anthropic.isConfigured()) {
            log.info("Tarif-Crawler liest mit Claude ({})", anthropic.getTariffModel());
            return new AnthropicTariffExtractClient(anthropic, objectMapper);
        }
        log.info("Tarif-Crawler liest mit Mistral ({})", mistral.getTariffModel());
        return new MistralTariffExtractClient(mistralTariffRestTemplate, mistral, objectMapper);
    }
}
