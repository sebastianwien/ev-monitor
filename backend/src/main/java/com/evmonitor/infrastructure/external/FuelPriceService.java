package com.evmonitor.infrastructure.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import com.evmonitor.application.FuelPriceResponse;
import com.evmonitor.infrastructure.persistence.FuelPriceMonthEntity;
import com.evmonitor.infrastructure.persistence.JpaFuelPriceMonthRepository;

import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;

/**
 * Fetches current national average fuel prices from Tankerkoenig API (v4/stats).
 * Refreshed daily at 6 AM. Falls back to hardcoded defaults if API key missing or call fails.
 *
 * <p>Jeder erfolgreiche Abruf überschreibt den Benzinpreis des laufenden Monats in
 * {@code fuel_price_month}. Nach dem Monatswechsel bleibt dort der letzte Wert stehen; Monatsmail und
 * Story-Rückblick rechnen einen vergangenen Monat damit, damit beide dieselbe Ersparnis zeigen.
 *
 * API: https://creativecommons.tankerkoenig.de/api/v4/stats?apikey=KEY
 * Key beantragen: https://creativecommons.tankerkoenig.de/
 */
@Service
@Slf4j
public class FuelPriceService {

    /** Assumed combustion-car consumption for every petrol/diesel comparison (ticker, recap, model ranking). */
    public static final BigDecimal COMBUSTION_LITERS_PER_100_KM = new BigDecimal("7.0");

    // Fallback prices (updated roughly quarterly)
    private static final double FALLBACK_BENZIN = 2.15;
    private static final double FALLBACK_DIESEL = 2.32;

    @Value("${tankerkoenig.api-key:}")
    private String apiKey;

    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    private final ObjectMapper objectMapper;
    private final JpaFuelPriceMonthRepository months;

    private volatile double benzinPrice = FALLBACK_BENZIN;
    private volatile double dieselPrice = FALLBACK_DIESEL;
    /** Zeitpunkt des letzten erfolgreichen Abrufs, null solange Fallback gilt. */
    private volatile Instant updatedAt = null;

    public FuelPriceService(ObjectMapper objectMapper, JpaFuelPriceMonthRepository months) {
        this.objectMapper = objectMapper;
        this.months = months;
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

                    apply(benzin, diesel, Instant.now());

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

    /** Übernimmt einen erfolgreichen Abruf und schreibt den Benzinpreis in die Zeile des laufenden Monats. */
    void apply(double benzin, double diesel, Instant at) {
        if (benzin > 0) benzinPrice = benzin;
        if (diesel > 0) dieselPrice = diesel;
        updatedAt = at;
        if (benzin <= 0) {
            return;
        }
        try {
            months.save(new FuelPriceMonthEntity(YearMonth.from(at.atZone(ZONE)).atDay(1),
                    BigDecimal.valueOf(benzin).setScale(3, java.math.RoundingMode.HALF_UP), at));
        } catch (RuntimeException e) {
            // Der Tagespreis gilt trotzdem; der Monat holt sich den Wert beim nächsten Abruf.
            log.warn("FuelPriceService: Monatspreis nicht gespeichert: {}", e.getMessage());
        }
    }

    /**
     * Benzinpreis eines Monats: der gespeicherte Monatswert, sonst der heutige Preis (Monate vor dem
     * ersten Abruf, Speicher nicht erreichbar).
     */
    public double getBenzinPrice(YearMonth month) {
        try {
            return months.findById(month.atDay(1))
                    .map(m -> m.getBenzinEurPerL().doubleValue())
                    .orElse(benzinPrice);
        } catch (RuntimeException e) {
            log.warn("FuelPriceService: Monatspreis nicht lesbar: {}", e.getMessage());
            return benzinPrice;
        }
    }

    /** Mittelwert aus Benzin (E5/E10) und Diesel */
    public double getAvgFuelPrice() {
        return (benzinPrice + dieselPrice) / 2.0;
    }

    public double getBenzinPrice() { return benzinPrice; }

    /** Momentaufnahme fuer die oeffentliche API. */
    public FuelPriceResponse snapshot() {
        return new FuelPriceResponse(benzinPrice, dieselPrice, updatedAt,
                getBenzinPrice(YearMonth.now(ZONE).minusMonths(1)));
    }
    public double getDieselPrice() { return dieselPrice; }
}
