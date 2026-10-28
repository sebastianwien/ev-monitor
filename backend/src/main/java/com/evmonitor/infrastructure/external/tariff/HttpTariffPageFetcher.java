package com.evmonitor.infrastructure.external.tariff;

import com.evmonitor.application.tariff.crawl.PageText;
import com.evmonitor.application.tariff.crawl.RobotsRules;
import com.evmonitor.application.tariff.crawl.TariffCrawlProperties;
import com.evmonitor.application.tariff.crawl.TariffPageFetcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lädt Tarifseiten wie ein höflicher Bot: eigener User-Agent mit Kontakt, robots.txt zuerst, nur
 * https auf Port 443, nur öffentliche Hosts (auch nach einer Weiterleitung, höchstens drei), nur
 * HTML, höchstens {@code tariff-crawl.max-bytes}. Die Quell-URLs pflegt nur der Admin; der
 * Host-Check verhindert trotzdem, dass eine Weiterleitung ins interne Netz führt.
 */
@Component
@Slf4j
public class HttpTariffPageFetcher implements TariffPageFetcher {

    private static final int MAX_REDIRECTS = 3;
    private static final Pattern CHARSET = Pattern.compile("(?i)charset=\"?([\\w-]+)");

    /** Löst den Host auf und lehnt ab, wenn eine Adresse nicht öffentlich ist. */
    static final Predicate<String> PUBLIC_HOST = host -> {
        try {
            for (InetAddress a : InetAddress.getAllByName(host)) {
                if (a.isLoopbackAddress() || a.isSiteLocalAddress() || a.isLinkLocalAddress() || a.isAnyLocalAddress()
                        || a.isMulticastAddress() || (a instanceof Inet6Address && (a.getAddress()[0] & 0xfe) == 0xfc)) {
                    return false;
                }
            }
            return true;
        } catch (UnknownHostException e) {
            return false;
        }
    };

    private final TariffCrawlProperties props;
    private final Predicate<String> hostGuard;
    private final boolean allowInsecure;
    private final int maxBytes;
    private final HttpClient client;

    @Autowired
    public HttpTariffPageFetcher(TariffCrawlProperties props) {
        this(props, PUBLIC_HOST, false, props.getMaxBytes());
    }

    /** {@code allowInsecure} nur für Tests: erlaubt http, IP-Adressen und fremde Ports. */
    HttpTariffPageFetcher(TariffCrawlProperties props, Predicate<String> hostGuard, boolean allowInsecure, int maxBytes) {
        this.props = props;
        this.hostGuard = hostGuard;
        this.allowInsecure = allowInsecure;
        this.maxBytes = maxBytes;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(props.getTimeoutSeconds()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    static boolean isAllowedUrl(String url, boolean allowInsecure, Predicate<String> hostGuard) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            return false;
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost();
        if (host == null || uri.getUserInfo() != null) return false;
        if (allowInsecure) return (scheme.equals("https") || scheme.equals("http")) && hostGuard.test(host);
        if (!scheme.equals("https") || (uri.getPort() != -1 && uri.getPort() != 443)) return false;
        if (host.startsWith("[") || host.matches("[0-9.]+")) return false;
        return hostGuard.test(host);
    }

    @Override
    public Fetched fetch(String url) {
        Map<String, RobotsRules> robotsByHost = new HashMap<>();
        String current = url;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            if (!isAllowedUrl(current, allowInsecure, hostGuard)) {
                return Fetched.failed(Outcome.BLOCKED_URL, 0, "URL nicht erlaubt: " + abbreviate(current));
            }
            URI uri = URI.create(current);
            RobotsRules robots = robotsByHost.computeIfAbsent(origin(uri), this::robots);
            if (!robots.allows(pathAndQuery(uri))) {
                return Fetched.failed(Outcome.ROBOTS, 0, "robots.txt sperrt " + uri.getPath());
            }
            Fetched result;
            try {
                HttpResponse<InputStream> res = client.send(request(uri), HttpResponse.BodyHandlers.ofInputStream());
                int status = res.statusCode();
                if (status >= 300 && status < 400 && res.headers().firstValue("Location").isPresent()) {
                    res.body().close();
                    current = uri.resolve(res.headers().firstValue("Location").get()).toString();
                    continue;
                }
                result = read(res, current);
            } catch (IOException e) {
                return Fetched.failed(Outcome.UNREACHABLE, 0, "nicht erreichbar: " + e.getClass().getSimpleName());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Fetched.failed(Outcome.UNREACHABLE, 0, "unterbrochen");
            }
            return result;
        }
        return Fetched.failed(Outcome.UNREACHABLE, 0, "zu viele Weiterleitungen");
    }

    private Fetched read(HttpResponse<InputStream> res, String url) throws IOException {
        int status = res.statusCode();
        try (InputStream in = res.body()) {
            if (status != 200) return Fetched.failed(Outcome.HTTP_ERROR, status, "HTTP " + status);
            String type = res.headers().firstValue("Content-Type").orElse("");
            if (!type.toLowerCase(Locale.ROOT).contains("html")) return Fetched.failed(Outcome.NOT_HTML, status, "kein HTML: " + abbreviate(type));
            byte[] bytes = in.readNBytes(maxBytes + 1);
            if (bytes.length > maxBytes) return Fetched.failed(Outcome.TOO_LARGE, status, "größer als " + maxBytes + " Bytes");
            return Fetched.ok(status, PageText.of(new String(bytes, charset(type)), url));
        }
    }

    /** RFC 9309: 4xx = keine Regeln, 5xx oder nicht erreichbar = alles gesperrt. */
    private RobotsRules robots(String origin) {
        try {
            HttpResponse<InputStream> res = client.send(request(URI.create(origin + "/robots.txt")), HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream in = res.body()) {
                int status = res.statusCode();
                if (status == 200) return RobotsRules.parse(new String(in.readNBytes(500_000), StandardCharsets.UTF_8), props.robotsAgent());
                if (status >= 400 && status < 500) return RobotsRules.parse("", props.robotsAgent());
                if (status >= 300 && status < 400) return RobotsRules.parse("", props.robotsAgent());
                return RobotsRules.parse("User-agent: *\nDisallow: /\n", props.robotsAgent());
            }
        } catch (IOException e) {
            return RobotsRules.parse("User-agent: *\nDisallow: /\n", props.robotsAgent());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return RobotsRules.parse("User-agent: *\nDisallow: /\n", props.robotsAgent());
        }
    }

    private HttpRequest request(URI uri) {
        return HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(props.getTimeoutSeconds()))
                .header("User-Agent", props.getUserAgent())
                .header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.5")
                .header("Accept-Language", "de-DE,de;q=0.9")
                .GET()
                .build();
    }

    private static String origin(URI uri) {
        return uri.getScheme() + "://" + uri.getHost() + (uri.getPort() == -1 ? "" : ":" + uri.getPort());
    }

    private static String pathAndQuery(URI uri) {
        String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        return uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
    }

    private static Charset charset(String contentType) {
        Matcher m = CHARSET.matcher(contentType);
        if (m.find()) {
            try {
                return Charset.forName(m.group(1));
            } catch (IllegalArgumentException e) {
                log.debug("Unbekannter Zeichensatz {}, nehme UTF-8", m.group(1));
            }
        }
        return StandardCharsets.UTF_8;
    }

    private static String abbreviate(String s) {
        return s.length() > 120 ? s.substring(0, 120) + "…" : s;
    }
}
