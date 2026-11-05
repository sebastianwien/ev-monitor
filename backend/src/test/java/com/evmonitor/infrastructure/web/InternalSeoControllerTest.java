package com.evmonitor.infrastructure.web;

import com.evmonitor.application.seo.BotHitService;
import com.evmonitor.application.seo.CrawlerBot;
import com.evmonitor.domain.User;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** nginx meldet Bot-Abrufe per Mirror: nur mit internem Token, gezählt wird Bot und Pfad, nichts weiter. */
class InternalSeoControllerTest extends AbstractIntegrationTest {

    private static final String URL = "/api/internal/seo/bot-hit";

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private BotHitService bots;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM seo_bot_daily");
    }

    private HttpEntity<Void> hit(String token, String userAgent, String uri) {
        HttpHeaders h = new HttpHeaders();
        if (token != null) h.set("X-Internal-Token", token);
        h.set("User-Agent", userAgent);
        h.set("X-Original-URI", uri);
        return new HttpEntity<>(h);
    }

    @Test
    void rejectsWithoutToken() {
        ResponseEntity<String> res = restTemplate.exchange(URL, HttpMethod.POST, hit(null, "GPTBot/1.2", "/modelle"), String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM seo_bot_daily", Integer.class)).isZero();
    }

    @Test
    void countsKnownBotsPerNormalizedPathAndIgnoresOthers() {
        String gpt = "Mozilla/5.0 (compatible; GPTBot/1.2; +https://openai.com/gptbot)";
        for (String uri : List.of("/modelle/Tesla/Model%20Y?utm=x", "/modelle/Tesla/Model_Y/", "/modelle")) {
            assertThat(restTemplate.exchange(URL, HttpMethod.POST, hit("test-internal-token", gpt, uri), String.class).getStatusCode())
                    .isEqualTo(HttpStatus.NO_CONTENT);
        }
        restTemplate.exchange(URL, HttpMethod.POST, hit("test-internal-token", "Mozilla/5.0 Safari/604.1", "/modelle"), String.class);
        restTemplate.exchange(URL, HttpMethod.POST, hit("test-internal-token", "Googlebot/2.1", "/modelle"), String.class);

        assertThat(jdbc.queryForObject("SELECT hits FROM seo_bot_daily WHERE bot = 'GPTBOT' AND path = '/modelle/Tesla/Model_Y'", Integer.class))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM seo_bot_daily", Integer.class)).isEqualTo(3);

        List<BotHitService.BotSummary> summary = bots.summary(7);
        BotHitService.BotSummary gptRow = summary.stream().filter(s -> s.bot() == CrawlerBot.GPTBOT).findFirst().orElseThrow();
        assertThat(gptRow.hits()).isEqualTo(3);
        assertThat(gptRow.ai()).isTrue();
        assertThat(gptRow.topPaths().get(0).path()).isEqualTo("/modelle/Tesla/Model_Y");
        assertThat(summary.get(0).bot()).as("KI-Crawler zuerst").isEqualTo(CrawlerBot.GPTBOT);
    }

    @Test
    void adminViewIsAdminOnly() {
        User user = createAndSaveUser("bots-user-" + System.nanoTime() + "@example.com");
        User admin = createAndSaveAdminUser("bots-admin-" + System.nanoTime() + "@example.com");

        assertThat(restTemplate.exchange("/api/admin/seo/bots?days=28", HttpMethod.GET, createAuthRequest(user.getId(), user.getEmail()), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ResponseEntity<String> ok = restTemplate.exchange("/api/admin/seo/bots?days=28", HttpMethod.GET,
                createAuthRequest(admin.getId(), admin.getEmail()), String.class);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ok.getBody()).startsWith("[");
        assertThat(restTemplate.exchange("/api/admin/seo/bots?days=0", HttpMethod.GET,
                createAuthRequest(admin.getId(), admin.getEmail()), String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
