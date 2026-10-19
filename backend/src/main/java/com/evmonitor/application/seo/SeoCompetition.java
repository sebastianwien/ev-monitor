package com.evmonitor.application.seo;

import java.time.LocalDate;
import java.util.List;

/** Antwort-DTOs des Unter-Tabs "Wettbewerb". Nur öffentliche Suchergebnisse und Google-Ads-Kennzahlen. */
public final class SeoCompetition {

    private SeoCompetition() {
    }

    /** Fremde Domain über alle beobachteten Suchbegriffe: wie oft in den Top 10, wie oft vor uns, mittlerer Platz. */
    public record CompetitorRow(String domain, int queries, int aheadOfUs, double avgRank, int bestRank) {
    }

    public record SerpEntry(int rank, String domain, String url, String title, boolean own) {
    }

    /** ourRank null = nicht unter den ersten zehn. volume/cpc aus dem letzten Monat, null ohne Daten. */
    public record SerpQueryRow(String query, long impressions, Integer ourRank, String ourUrl, Integer volume, Double cpc,
                               List<SerpEntry> top) {
    }

    /** covered: für den Begriff erschien in den letzten 90 Tagen schon eine unserer deutschen Seiten bei Google. */
    public record IdeaRow(String keyword, String seed, Integer volume, Double cpc, boolean covered) {
    }

    /** Suchvolumen und CPC je Begriff für die Spalten im Unter-Tab Suchbegriffe. */
    public record KeywordRow(String query, Integer volume, Double cpc, String competition) {
    }

    public record View(boolean enabled, LocalDate serpWeek, LocalDate metricsMonth, double costThisMonthUsd,
                       double monthlyBudgetUsd, List<CompetitorRow> competitors, List<SerpQueryRow> queries,
                       List<IdeaRow> ideas) {
    }
}
