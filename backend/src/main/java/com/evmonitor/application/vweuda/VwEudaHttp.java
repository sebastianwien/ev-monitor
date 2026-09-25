package com.evmonitor.application.vweuda;

import java.util.List;
import java.util.Map;

/**
 * Minimaler HTTP-Zugang fuer den Portal-Client: ein Roundtrip, KEIN Redirect-Folgen,
 * keine Cookie-Verwaltung - beides macht {@link VwEudaLoginClient} selbst, damit jede
 * Zwischenstation (Cookies, Fehlerseiten) sichtbar bleibt und der Client testbar ist.
 */
public interface VwEudaHttp {

    record Request(String method, String url, Map<String, String> headers, String formBody) {
        static Request get(String url, Map<String, String> headers) {
            return new Request("GET", url, headers, null);
        }
        static Request postForm(String url, Map<String, String> headers, String formBody) {
            return new Request("POST", url, headers, formBody);
        }
    }

    record Response(int status, Map<String, List<String>> headers, byte[] body) {
        String header(String name) {
            return headers.entrySet().stream()
                    .filter(e -> e.getKey().equalsIgnoreCase(name))
                    .flatMap(e -> e.getValue().stream())
                    .findFirst().orElse(null);
        }
        List<String> headerValues(String name) {
            return headers.entrySet().stream()
                    .filter(e -> e.getKey().equalsIgnoreCase(name))
                    .flatMap(e -> e.getValue().stream())
                    .toList();
        }
        String text() {
            return new String(body, java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    Response send(Request request);
}
