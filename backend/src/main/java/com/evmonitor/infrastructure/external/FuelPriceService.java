package com.evmonitor.infrastructure.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Fetches current national average fuel prices from Tankerkoenig API (v4/stats).
 * Refreshed daily at 6 AM. Falls back to hardcoded defaults if API key missing or call fails.
 *
 * API: https://creativecommons.tankerkoenig.de/api/v4/stats?apikey=KEY
 * Key beantragen: https://creativecommons.tankerkoenig.de/
 */
@Service
public class FuelPriceService {

    private static final Logger log = LoggerFactory.getLogger(FuelPriceService.class);

    // Fallback prices (updated roughly quarterly)
    private static final double FALLBACK_BENZIN = 2.15;
    private static final double FALLBACK_DIESEL = 2.32;

    @Value("${tankerkoenig.api-key:}")
    private String apiKey;

    private final ObjectMapper objectMapper;

    private volatile double benzinPrice = FALLBACK_BENZIN;
    private volatile double dieselPrice = FALLBACK_DIESEL;

    public FuelPriceService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        refresh();
    }

    @Scheduled(cron = "0 0 6 * * *")
    public void refresh() {
        if (apiKey == null || apiKey.isBlank()) {
            log.debug("FuelPriceService: no TANKERKOENIG_API_KEY configured, using fallback prices");
            return;
        }
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://creativecommons.tankerkoenig.de/api/v4/stats?apikey=" + apiKey))
                    .timeout(Duration.ofSeconds(8))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                double e5 = root.path("E5").path("mean").asDouble(0);
                double e10 = root.path("E10").path("mean").asDouble(0);
                double diesel = root.path("Diesel").path("mean").asDouble(0);

                if (e5 > 0 || e10 > 0 || diesel > 0) {
                    // Benzin = Mittelwert E5 + E10 (beide Super-Sorten)
                    double benzin = (e5 > 0 && e10 > 0) ? (e5 + e10) / 2.0
                            : (e5 > 0 ? e5 : e10);

                    if (benzin > 0) benzinPrice = benzin;
                    if (diesel > 0) dieselPrice = diesel;

                    log.info("FuelPriceService: Benzin={} €/L, Diesel={} €/L (Tankerkoenig national avg)",
                            String.format("%.3f", benzinPrice), String.format("%.3f", dieselPrice));
                } else {
                    log.warn("FuelPriceService: unexpected response format: {}", response.body());
                }
            } else {
                log.warn("FuelPriceService: HTTP {}", response.statusCode());
            }
        } catch (Exception e) {
            log.warn("FuelPriceService: fetch failed, using cached prices: {}", e.getMessage());
        }
    }

    /** Mittelwert aus Benzin (E5/E10) und Diesel */
    public double getAvgFuelPrice() {
        return (benzinPrice + dieselPrice) / 2.0;
    }

    public double getBenzinPrice() { return benzinPrice; }
    public double getDieselPrice() { return dieselPrice; }
}
