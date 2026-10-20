package com.evmonitor.infrastructure.external.audit;

import com.evmonitor.application.seo.SeoAuditProperties;
import com.evmonitor.infrastructure.external.audit.SiteCrawler.Fetched;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Erfundene Domain; HttpClient gemockt. */
class SiteCrawlerTest {

    private final HttpClient http = mock(HttpClient.class);
    private final SeoAuditProperties properties = new SeoAuditProperties();
    private final SiteCrawler crawler;

    SiteCrawlerTest() {
        properties.setBaseUrl("https://example.test/");
        crawler = new SiteCrawler(properties, http);
    }

    @SuppressWarnings("unchecked")
    private void respond(int status, String body) throws Exception {
        HttpResponse<InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
    }

    @Test
    @SuppressWarnings("unchecked")
    void sitemapPathsAreNormalizedAndDeduplicated() throws Exception {
        respond(200, "<urlset><url><loc>https://example.test/</loc></url><url><loc> https://example.test/a/B%20c/ </loc></url>"
                + "<url><loc>https://example.test/a/B_c</loc></url></urlset>");

        assertThat(crawler.sitemapPaths()).containsExactly("/", "/a/B_c");

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(captor.getValue().uri().toString()).isEqualTo("https://example.test/sitemap.xml");
        assertThat(captor.getValue().headers().firstValue("User-Agent")).get().asString().contains("Googlebot");
    }

    @Test
    void sitemapErrorThrows() throws Exception {
        respond(502, "");
        assertThatThrownBy(crawler::sitemapPaths).isInstanceOf(SiteCrawlerException.class).hasMessageContaining("502");
    }

    @Test
    @SuppressWarnings("unchecked")
    void encodesUmlautsAndKeepsStatusOfRedirect() throws Exception {
        respond(301, "");

        assertThat(crawler.fetch("/modelle/Škoda/Elroq")).isEqualTo(new Fetched(301, ""));

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(captor.getValue().uri().toString()).isEqualTo("https://example.test/modelle/%C5%A0koda/Elroq");
    }

    @Test
    void tooLargeOrUnreachableIsStatusZero() throws Exception {
        properties.setMaxBytes(100);
        respond(200, "x".repeat(101));
        assertThat(crawler.fetch("/gross")).isEqualTo(new Fetched(0, null));

        when(http.send(any(HttpRequest.class), any())).thenThrow(new IOException("timeout"));
        assertThat(crawler.fetch("/weg")).isEqualTo(new Fetched(0, null));
    }
}
