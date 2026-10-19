package com.evmonitor.application.seo;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Unter-Tab "Wettbewerb" mit DataForSEO. Zugang ist Login plus API-Passwort (app.dataforseo.com/api-access), beide
 * nur als Env; leer = aus. Das Monatsbudget bricht Abrufe ab, bevor es überschritten wird.
 */
@ConfigurationProperties(prefix = "seo.competition")
@Component
@Getter
@Setter
public class SeoCompetitionProperties {

    private String login = "";
    /** Unsere Domain in den Suchergebnissen, ohne "www.". */
    private String ownDomain = "ev-monitor.net";
    private String password = "";

    /** Google Deutschland, Sprache Deutsch, mobil wie die Mehrheit unserer Besucher. */
    private int locationCode = 2276;
    private String languageCode = "de";
    private String device = "mobile";

    /** So viele Non-Brand-Suchbegriffe der deutschen Website (nach Impressionen, 28 Tage) bekommen wöchentlich ihre Top 10. */
    private int topQueries = 50;
    private int serpDepth = 10;

    /** Aus so vielen Top-Begriffen werden monatlich Begriffsideen geholt, je Begriff höchstens suggestionLimit. */
    private int suggestionSeeds = 10;
    private int suggestionLimit = 50;

    /** Höchstens so viele Begriffe bekommen monatlich Suchvolumen und CPC (1 000 je Abruf). */
    private int volumeMaxKeywords = 5000;

    /** Obergrenze der Kosten je Kalendermonat in US-Dollar. */
    private double monthlyBudgetUsd = 5.0;

    private int retentionDays = 490;

    public boolean isEnabled() {
        return login != null && !login.isBlank() && password != null && !password.isBlank();
    }
}
