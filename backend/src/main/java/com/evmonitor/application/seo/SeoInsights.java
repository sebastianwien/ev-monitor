package com.evmonitor.application.seo;

import java.time.LocalDate;
import java.util.List;

/** Antwort-DTOs des SEO-Tabs. Nur Aggregate, keine Nutzerdaten. */
public final class SeoInsights {

    private SeoInsights() {
    }

    public enum PageFilter { ALL, UNDERRATED, DEAD }

    public enum QueryFilter { ALL, STRIKING_DISTANCE, CANNIBALIZED }

    public enum BrandFilter { ALL, BRAND, NON_BRAND }

    /** registrations: angelegte Konten im Zeitraum (DB), organicRegistrations: davon mit Google-Verweis ohne bezahltes Medium. */
    public record Totals(long impressions, long clicks, double ctr, double avgPosition,
                         long registrations, long organicRegistrations) {
        public static final Totals EMPTY = new Totals(0, 0, 0, 0, 0, 0);
    }

    public record DayPoint(LocalDate day, long impressions, long clicks) {
    }

    public record Overview(int periodDays, LocalDate from, LocalDate to, Totals current, Totals previous,
                           List<DayPoint> days) {
    }

    /** finding: UNDERRATED, DEAD oder null; registrations: Konten, deren erste Seite dieser Pfad war. */
    public record PageRow(String path, long impressions, long clicks, double ctr, double avgPosition,
                          long registrations, String finding) {
    }

    /** finding: STRIKING_DISTANCE, CANNIBALIZED oder null; topPath = Seite mit den meisten Klicks. */
    public record QueryRow(String query, boolean brand, long impressions, long clicks, double ctr, double avgPosition,
                           String topPath, int pageCount, String finding) {
    }
}
