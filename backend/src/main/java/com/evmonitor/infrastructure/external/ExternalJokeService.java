package com.evmonitor.infrastructure.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Fetches jokes from JokeAPI.dev (German, safe-mode) and caches them in memory.
 * Refreshed daily. Falls back silently to empty list if API is unreachable.
 */
@Component
public class ExternalJokeService {

    private static final Logger log = LoggerFactory.getLogger(ExternalJokeService.class);
    private static final String API_URL =
            "https://v2.jokeapi.dev/joke/Any?lang=de&safe-mode" +
            "&blacklistFlags=nsfw,religious,political,racist,sexist,explicit" +
            "&type=single&amount=10";

    private volatile List<String> cachedJokes = new ArrayList<>();

    private final ObjectMapper objectMapper;

    public ExternalJokeService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        refreshJokes();
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void refreshJokes() {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .timeout(Duration.ofSeconds(8))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                List<String> jokes = new ArrayList<>();

                if (root.has("jokes")) {
                    for (JsonNode j : root.get("jokes")) {
                        String text = j.get("joke").asText().trim();
                        if (!text.isBlank()) jokes.add(text);
                    }
                } else if (root.has("joke")) {
                    String text = root.get("joke").asText().trim();
                    if (!text.isBlank()) jokes.add(text);
                }

                if (!jokes.isEmpty()) {
                    cachedJokes = Collections.unmodifiableList(jokes);
                    log.info("ExternalJokeService: loaded {} jokes from JokeAPI", jokes.size());
                }
            } else {
                log.warn("ExternalJokeService: JokeAPI returned status {}", response.statusCode());
            }
        } catch (Exception e) {
            log.warn("ExternalJokeService: could not fetch jokes, using cached ({} jokes): {}",
                    cachedJokes.size(), e.getMessage());
        }
    }

    public List<String> getJokes() {
        return cachedJokes;
    }
}
