package com.evmonitor.infrastructure.external.tariff;

import com.evmonitor.application.tariff.crawl.TariffCrawlProperties;
import com.evmonitor.application.tariff.crawl.TariffPageFetcher.Fetched;
import com.evmonitor.application.tariff.crawl.TariffPageFetcher.Outcome;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fetches a tariff page like a polite bot: own User-Agent, robots.txt first, only public
 * hosts (also after a redirect), only HTML, at most a few megabytes. Never throws.
 */
class HttpTariffPageFetcherTest {

    private HttpServer server;
    private final Map<String, String[]> routes = new HashMap<>();
    private final Map<String, String> seenAgents = new HashMap<>();
    private String base;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", ex -> {
            String path = ex.getRequestURI().getPath();
            seenAgents.put(path, ex.getRequestHeaders().getFirst("User-Agent"));
            String[] r = routes.getOrDefault(path, new String[]{"404", "text/html", "nope"});
            if (r[0].equals("302")) ex.getResponseHeaders().add("Location", r[2]);
            ex.getResponseHeaders().add("Content-Type", r[1]);
            byte[] body = r[0].equals("302") ? new byte[0] : r[2].getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(Integer.parseInt(r[0]), body.length == 0 ? -1 : body.length);
            if (body.length > 0) ex.getResponseBody().write(body);
            ex.close();
        });
        server.start();
        base = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    /** Test-Fetcher: Loopback und http erlaubt, sonst wie in Produktion. */
    private HttpTariffPageFetcher fetcher(int maxBytes) {
        return new HttpTariffPageFetcher(new TariffCrawlProperties(), host -> true, true, maxBytes);
    }

    @Test
    void htmlPage_isReadAsText_withOurUserAgent() {
        routes.put("/tarife", new String[]{"200", "text/html; charset=utf-8", "<p>DC: 0,39 €/kWh</p>"});

        Fetched f = fetcher(100_000).fetch(base + "/tarife");

        assertThat(f.outcome()).isEqualTo(Outcome.OK);
        assertThat(f.page().text()).isEqualTo("DC: 0,39 €/kWh");
        assertThat(seenAgents.get("/tarife")).startsWith("EV-Monitor-Tarifcheck/");
        assertThat(seenAgents).containsKey("/robots.txt");
    }

    @Test
    void robotsDisallow_isRespected_beforeThePageIsRequested() {
        routes.put("/robots.txt", new String[]{"200", "text/plain", "User-agent: *\nDisallow: /tarife\n"});
        routes.put("/tarife", new String[]{"200", "text/html", "<p>x</p>"});

        Fetched f = fetcher(100_000).fetch(base + "/tarife");

        assertThat(f.outcome()).isEqualTo(Outcome.ROBOTS);
        assertThat(seenAgents).doesNotContainKey("/tarife");
    }

    @Test
    void httpError_isReported() {
        Fetched f = fetcher(100_000).fetch(base + "/gone");

        assertThat(f.outcome()).isEqualTo(Outcome.HTTP_ERROR);
        assertThat(f.httpStatus()).isEqualTo(404);
    }

    @Test
    void redirect_isFollowed_andTheTargetChecked() {
        routes.put("/alt", new String[]{"302", "text/html", "/neu"});
        routes.put("/neu", new String[]{"200", "text/html", "<p>neu</p>"});

        assertThat(fetcher(100_000).fetch(base + "/alt").page().text()).isEqualTo("neu");
    }

    @Test
    void redirectToABlockedHost_isRefused() {
        routes.put("/alt", new String[]{"302", "text/html", "http://169.254.169.254/latest/meta-data"});
        HttpTariffPageFetcher guarded = new HttpTariffPageFetcher(new TariffCrawlProperties(),
                host -> host.equals("localhost"), true, 100_000);

        assertThat(guarded.fetch(base + "/alt").outcome()).isEqualTo(Outcome.BLOCKED_URL);
    }

    @Test
    void nonHtml_andOversized_areRefused() {
        routes.put("/preise.pdf", new String[]{"200", "application/pdf", "%PDF-1.4"});
        routes.put("/riesig", new String[]{"200", "text/html", "<p>" + "x".repeat(5_000) + "</p>"});

        assertThat(fetcher(1_000).fetch(base + "/preise.pdf").outcome()).isEqualTo(Outcome.NOT_HTML);
        assertThat(fetcher(1_000).fetch(base + "/riesig").outcome()).isEqualTo(Outcome.TOO_LARGE);
    }

    @Test
    void productionRules_onlyHttps_noIpLiterals_noPrivateNetworks() {
        assertThat(HttpTariffPageFetcher.isAllowedUrl("http://www.enbw.com/x", false, h -> true)).isFalse();
        assertThat(HttpTariffPageFetcher.isAllowedUrl("https://127.0.0.1/x", false, h -> true)).isFalse();
        assertThat(HttpTariffPageFetcher.isAllowedUrl("https://[::1]/x", false, h -> true)).isFalse();
        assertThat(HttpTariffPageFetcher.isAllowedUrl("https://user@www.enbw.com/x", false, h -> true)).isFalse();
        assertThat(HttpTariffPageFetcher.isAllowedUrl("https://www.enbw.com:8443/x", false, h -> true)).isFalse();
        assertThat(HttpTariffPageFetcher.isAllowedUrl("https://www.enbw.com/x", false, h -> true)).isTrue();
        assertThat(HttpTariffPageFetcher.PUBLIC_HOST.test("localhost")).isFalse();
    }
}
