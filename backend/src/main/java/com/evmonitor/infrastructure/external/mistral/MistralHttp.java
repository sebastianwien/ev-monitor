package com.evmonitor.infrastructure.external.mistral;

import com.evmonitor.application.voice.VoiceProviderException;
import com.evmonitor.application.voice.VoiceProviderException.Reason;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;

import java.util.function.Supplier;

/**
 * Gemeinsame Aufrufregeln: Bearer-Key, kein Retry bei 4xx ausser 429, dort kurz mit Backoff.
 * Fehlertexte von Mistral koennen Request-Inhalte zitieren und werden darum nie geloggt.
 */
final class MistralHttp {

    static final int RATE_LIMIT_RETRIES = 2;

    private MistralHttp() {}

    static HttpHeaders auth(MistralProperties props) {
        if (!props.isConfigured()) throw new VoiceProviderException(Reason.NOT_CONFIGURED, "MISTRAL_API_KEY fehlt");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(props.getApiKey());
        return headers;
    }

    static <T> T call(Supplier<T> request, long backoffMs) {
        for (int attempt = 0; ; attempt++) {
            try {
                return request.get();
            } catch (HttpStatusCodeException e) {
                boolean rateLimited = e.getStatusCode().value() == HttpStatus.TOO_MANY_REQUESTS.value();
                if (rateLimited && attempt < RATE_LIMIT_RETRIES) {
                    sleep(backoffMs << attempt);
                    continue;
                }
                if (rateLimited) throw new VoiceProviderException(Reason.RATE_LIMITED, "Mistral 429");
                Reason reason = e.getStatusCode().is4xxClientError() ? Reason.REJECTED : Reason.UNAVAILABLE;
                throw new VoiceProviderException(reason, "Mistral " + e.getStatusCode().value());
            } catch (ResourceAccessException e) {
                throw new VoiceProviderException(Reason.UNAVAILABLE, "Mistral nicht erreichbar");
            }
        }
    }

    private static void sleep(long ms) {
        if (ms <= 0) return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new VoiceProviderException(Reason.UNAVAILABLE, "unterbrochen");
        }
    }
}
