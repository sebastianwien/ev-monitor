package com.evmonitor.application.seo;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Nächtlicher On-Page-Check (Unter-Tab "Technik"). Leere {@code baseUrl} = Crawl aus. Die Schwellen sind
 * SEO-Allgemeinwissen und dürfen öffentlich sein.
 */
@ConfigurationProperties(prefix = "seo.audit")
@Component
@Getter
@Setter
public class SeoAuditProperties {

    /** Von hier werden Sitemap und Seiten geholt, z. B. https://ev-monitor.net. Leer = aus. */
    private String baseUrl = "";

    /** Host, den Canonical und interne Links tragen müssen. */
    private String siteHost = "ev-monitor.net";

    /** Enthält "Googlebot", damit nginx wie bei Google an den Prerender weiterreicht. */
    private String userAgent = "Mozilla/5.0 (compatible; Googlebot/2.1; ev-monitor-seo-audit)";

    private int titleMin = 30;
    private int titleMax = 60;
    private int descriptionMin = 70;
    private int descriptionMax = 155;

    /** Pause zwischen zwei Abrufen, damit der Prerender nicht unter Last gerät. */
    private long delayMs = 200;
    private int timeoutSeconds = 30;
    /** Größere Antworten werden abgeschnitten und als nicht erreichbar gewertet. */
    private int maxBytes = 2_000_000;

    /** Linkziele außerhalb der Sitemap, die je Lauf zusätzlich geholt werden. */
    private int maxExtraUrls = 200;

    /** Sprachvarianten tragen absichtlich noindex; Links dorthin werden weder geholt noch gewertet. */
    private List<String> ignoredLinkPrefixes = List.of("/en", "/gb", "/no", "/se");

    /** Wie bei GSC: 16 Monate, älteres wird nach dem Lauf gelöscht. */
    private int retentionDays = 490;

    /** PageSpeed-Insights-Key; leer = keine Core Web Vitals. Nie loggen. */
    private String pagespeedApiKey = "";

    /** Wie viele Seiten (nach Impressionen der letzten 28 Tage) wöchentlich auf Core Web Vitals gemessen werden. */
    private int vitalsTopPages = 20;
    private int vitalsLcpMaxMs = 2500;
    private double vitalsClsMax = 0.1;
    private int vitalsInpMaxMs = 200;

    public boolean isEnabled() {
        return baseUrl != null && !baseUrl.isBlank();
    }

    public boolean isVitalsEnabled() {
        return isEnabled() && pagespeedApiKey != null && !pagespeedApiKey.isBlank();
    }
}
