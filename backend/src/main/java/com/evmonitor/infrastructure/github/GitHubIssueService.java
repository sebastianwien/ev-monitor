package com.evmonitor.infrastructure.github;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Creates GitHub Issues for unexpected errors so that the claude-fix workflow
 * can automatically analyse and open a PR.
 * Rate-limited identically to AlertEmailService (1 issue per error key per hour).
 */
@Service
@Slf4j
public class GitHubIssueService {

    private static final Duration COOLDOWN = Duration.ofHours(1);

    private final Map<String, Instant> lastIssueTime = new ConcurrentHashMap<>();
    private final RestClient restClient;

    @Value("${app.github.token:}")
    private String githubToken;

    @Value("${app.github.repo:}")
    private String githubRepo; // e.g. "sebiweise/ev-monitor"

    @Value("${app.github.issues.enabled:false}")
    private boolean enabled;

    public GitHubIssueService() {
        this.restClient = RestClient.builder()
                .baseUrl("https://api.github.com")
                .build();
    }

    /**
     * @param errorKey   Deduplication key (same as used in AlertEmailService)
     * @param title      Issue title
     * @param body       Issue body (markdown)
     */
    public void createIssue(String errorKey, String title, String body) {
        if (!enabled || githubToken.isBlank() || githubRepo.isBlank()) {
            log.debug("GitHub issue creation disabled or not configured");
            return;
        }

        Instant now = Instant.now();
        Instant last = lastIssueTime.get(errorKey);
        if (last != null && Duration.between(last, now).compareTo(COOLDOWN) < 0) {
            log.debug("GitHub issue rate-limited for key '{}'", errorKey);
            return;
        }
        lastIssueTime.put(errorKey, now);

        try {
            Map<String, Object> payload = Map.of(
                    "title", title,
                    "body", body,
                    "labels", List.of("auto-fix")
            );

            restClient.post()
                    .uri("/repos/" + githubRepo + "/issues")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + githubToken)
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();

            log.info("GitHub issue created for error key '{}'", errorKey);
        } catch (Exception e) {
            log.error("Failed to create GitHub issue: {}", e.getMessage());
        }
    }
}
