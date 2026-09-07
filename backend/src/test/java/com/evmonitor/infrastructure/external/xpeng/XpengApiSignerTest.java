package com.evmonitor.infrastructure.external.xpeng;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Signaturberechnung nach XPeng EU-Data-Act Integration Guide:
 * stringA = "body" + body + "nonce" + nonce   (Query-/Body-Keys aufsteigend sortiert)
 * stringB = appId + stringA + appSecret
 * sign    = SHA1(stringB) in Kleinbuchstaben (hex)
 */
class XpengApiSignerTest {

    @Test
    void computes_documented_golden_vector() {
        // Extern (Python-Referenz) berechneter Vektor - nagelt den Algorithmus fest.
        String sign = XpengApiSigner.sign("app123", "secret456", "{\"k\":\"v\"}", "abc");

        assertThat(sign).isEqualTo("5d269ef0e06838c2bc664423b2eda15da79cb5ab");
    }

    @Test
    void produces_lowercase_40_char_hex() {
        String sign = XpengApiSigner.sign("appId", "secret", "{}", "nonce");

        assertThat(sign).hasSize(40).matches("[0-9a-f]{40}");
    }

    @Test
    void is_sensitive_to_every_input_component() {
        String base = XpengApiSigner.sign("app", "secret", "{\"a\":1}", "n1");

        assertThat(XpengApiSigner.sign("APP", "secret", "{\"a\":1}", "n1")).isNotEqualTo(base);
        assertThat(XpengApiSigner.sign("app", "SECRET", "{\"a\":1}", "n1")).isNotEqualTo(base);
        assertThat(XpengApiSigner.sign("app", "secret", "{\"a\":2}", "n1")).isNotEqualTo(base);
        assertThat(XpengApiSigner.sign("app", "secret", "{\"a\":1}", "n2")).isNotEqualTo(base);
    }

    @Test
    void rejects_blank_credentials() {
        assertThatThrownBy(() -> XpengApiSigner.sign("", "secret", "{}", "n"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> XpengApiSigner.sign("app", "", "{}", "n"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
