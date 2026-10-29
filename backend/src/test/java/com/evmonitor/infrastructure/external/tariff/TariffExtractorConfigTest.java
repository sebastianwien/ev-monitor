package com.evmonitor.infrastructure.external.tariff;

import com.evmonitor.application.tariff.crawl.TariffExtractor;
import com.evmonitor.infrastructure.external.anthropic.AnthropicProperties;
import com.evmonitor.infrastructure.external.anthropic.AnthropicTariffExtractClient;
import com.evmonitor.infrastructure.external.mistral.MistralProperties;
import com.evmonitor.infrastructure.external.mistral.MistralTariffExtractClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** Claude, sobald ein Anthropic-Key gesetzt ist; sonst Mistral wie bisher. */
class TariffExtractorConfigTest {

    private final TariffExtractorConfig config = new TariffExtractorConfig();

    @Test
    void withAnthropicKey_claudeReads() {
        AnthropicProperties anthropic = new AnthropicProperties();
        anthropic.setApiKey("sk-ant-test");
        TariffExtractor extractor = config.tariffExtractor(anthropic, new MistralProperties(), new RestTemplate(), new ObjectMapper());
        assertThat(extractor).isInstanceOf(AnthropicTariffExtractClient.class);
        assertThat(extractor.isAvailable()).isTrue();
        ((AnthropicTariffExtractClient) extractor).close();
    }

    @Test
    void withoutAnthropicKey_mistralReads() {
        MistralProperties mistral = new MistralProperties();
        mistral.setApiKey("mistral-test");
        TariffExtractor extractor = config.tariffExtractor(new AnthropicProperties(), mistral, new RestTemplate(), new ObjectMapper());
        assertThat(extractor).isInstanceOf(MistralTariffExtractClient.class);
        assertThat(extractor.isAvailable()).isTrue();
    }
}
