package com.evmonitor.infrastructure.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Read-only raw REST client for the Stripe report. Uses the plain REST API with Jackson trees
 * instead of the Stripe SDK models on purpose: the SDK deserializer broke on newer API versions
 * (see StripeService webhook handling), and the report only needs a handful of fields.
 */
@Component
@Slf4j
public class StripeReportClient {

    private static final String BASE_URL = "https://api.stripe.com/v1/";
    private static final int PAGE_SIZE = 100;

    public record StripeRawData(
            List<JsonNode> subscriptions,
            List<JsonNode> invoices,
            List<JsonNode> balanceTransactions,
            List<JsonNode> products,
            JsonNode balance
    ) {}

    @Value("${stripe.secret-key:}")
    private String secretKey;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper objectMapper;

    public StripeReportClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Empty when no key is configured or Stripe is unreachable; the report then shows a hint. */
    public Optional<StripeRawData> fetchAll() {
        if (secretKey == null || secretKey.isBlank()) {
            log.warn("[STRIPE-REPORT] STRIPE_SECRET_KEY not configured");
            return Optional.empty();
        }
        try {
            return Optional.of(new StripeRawData(
                    list("subscriptions?status=all&expand[]=data.customer"),
                    list("invoices"),
                    list("balance_transactions"),
                    list("products?active=true"),
                    get("balance")));
        } catch (Exception e) {
            log.error("[STRIPE-REPORT] fetch failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private List<JsonNode> list(String path) throws IOException, InterruptedException {
        List<JsonNode> out = new ArrayList<>();
        String sep = path.contains("?") ? "&" : "?";
        String url = path + sep + "limit=" + PAGE_SIZE;
        while (url != null) {
            JsonNode page = get(url);
            for (JsonNode n : page.path("data")) {
                out.add(n);
            }
            url = page.path("has_more").asBoolean(false) && !out.isEmpty()
                    ? path + sep + "limit=" + PAGE_SIZE + "&starting_after=" + out.get(out.size() - 1).path("id").asText()
                    : null;
        }
        return out;
    }

    private JsonNode get(String pathWithQuery) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + pathWithQuery))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + secretKey)
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Stripe returned " + response.statusCode() + " for " + pathWithQuery.split("\\?")[0]);
        }
        return objectMapper.readTree(response.body());
    }
}
