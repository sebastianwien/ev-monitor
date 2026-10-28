package com.evmonitor.application.tariff.crawl;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Tarif-Crawler ({@code tariff-crawl.*}). Ohne Mistral-Key laufen nur WATCH-Quellen. */
@ConfigurationProperties(prefix = "tariff-crawl")
@Component
@Getter
@Setter
public class TariffCrawlProperties {

    private boolean enabled = true;
    /** Produktname plus Kontakt, damit Betreiber wissen, wer da liest. */
    private String userAgent = "EV-Monitor-Tarifcheck/1.0 (+https://ev-monitor.net)";
    private int timeoutSeconds = 20;
    private int maxBytes = 3_000_000;
    /** Eine Quelle wird höchstens so oft regulär gelesen. */
    private int refetchDays = 6;
    /** Ein offener Vorschlag wird frühestens nach so vielen Stunden bestätigt (zweiter Lauf). */
    private int confirmAfterHours = 20;
    /** Seitentext, der höchstens an das Sprachmodell geht. */
    private int maxTextChars = 60_000;

    /** Produktname ohne Version und Kontakt, für robots.txt. */
    public String robotsAgent() {
        int slash = userAgent.indexOf('/');
        return (slash > 0 ? userAgent.substring(0, slash) : userAgent).trim();
    }
}
