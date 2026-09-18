package com.evmonitor.application.euda;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Bewusst einfache Cookie-Ablage: Name/Wert je Domain. Ablaufdaten werden nicht ausgewertet -
 * ob eine Session noch gilt, entscheidet ohnehin das Portal. Ein Jar pro Verbindung.
 */
class CookieJar {

    /** domain -> (name -> value) */
    private final Map<String, Map<String, String>> byDomain = new TreeMap<>();

    void addFromResponse(String requestUrl, EudaHttp.Response response) {
        String host = URI.create(requestUrl).getHost();
        for (String setCookie : response.headerValues("Set-Cookie")) {
            String[] parts = setCookie.split(";");
            int eq = parts[0].indexOf('=');
            if (eq <= 0) continue;
            String name = parts[0].substring(0, eq).trim();
            String value = parts[0].substring(eq + 1).trim();
            String domain = host;
            for (int i = 1; i < parts.length; i++) {
                String attr = parts[i].trim();
                if (attr.toLowerCase(Locale.ROOT).startsWith("domain=")) {
                    domain = attr.substring(7).trim();
                    if (domain.startsWith(".")) domain = domain.substring(1);
                }
            }
            put(domain, name, value);
        }
    }

    void put(String domain, String name, String value) {
        byDomain.computeIfAbsent(domain, d -> new LinkedHashMap<>()).put(name, value);
    }

    /** Cookie-Header fuer einen Host - Domain-Match wie im Browser (Host oder Parent-Domain). */
    String headerFor(String url) {
        String host = URI.create(url).getHost();
        Map<String, String> merged = new LinkedHashMap<>();
        byDomain.forEach((domain, cookies) -> {
            if (host.equals(domain) || host.endsWith("." + domain)) merged.putAll(cookies);
        });
        return merged.isEmpty() ? null
                : merged.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue())
                        .collect(Collectors.joining("; "));
    }

    boolean has(String domain, String name) {
        return byDomain.getOrDefault(domain, Map.of()).containsKey(name);
    }

    Map<String, String> cookiesOf(String domain) {
        return Map.copyOf(byDomain.getOrDefault(domain, Map.of()));
    }

    void removeDomain(String domain) {
        byDomain.remove(domain);
    }
}
