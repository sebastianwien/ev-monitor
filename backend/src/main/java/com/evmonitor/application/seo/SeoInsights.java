package com.evmonitor.application.seo;

import java.time.LocalDate;
import java.util.List;

/** Antwort-DTOs des SEO-Tabs. Nur Aggregate, keine Nutzerdaten. Quoten als Anteil 0-1. */
public final class SeoInsights {

    private SeoInsights() {
    }

    public enum PageFilter { ALL, UNDERRATED, DEAD, LEAK, MONEY }

    public enum QueryFilter { ALL, STRIKING_DISTANCE, CANNIBALIZED, ADWORDS }

    public enum BrandFilter { ALL, BRAND, NON_BRAND }

    public enum ModelLevel { MODEL, BRAND }

    public enum ModelFilter { ALL, CONTENT_GAP, INVISIBLE }

    /**
     * visitors: Plausible-Besucher (Tagessumme), googleEntries: Visits mit Quelle Google; registrations: angelegte
     * Konten (DB), organicRegistrations: davon mit Google-Verweis ohne bezahltes Medium.
     */
    public record Totals(long impressions, long clicks, double ctr, double avgPosition,
                         long visitors, long googleEntries, long registrations, long organicRegistrations) {
        public static final Totals EMPTY = new Totals(0, 0, 0, 0, 0, 0, 0, 0);
    }

    public record DayPoint(LocalDate day, long impressions, long clicks, long visitors, long googleEntries) {
    }

    public record Overview(int periodDays, LocalDate from, LocalDate to, Totals current, Totals previous,
                           List<DayPoint> days) {
    }

    /**
     * finding: UNDERRATED, DEAD, LEAK, MONEY oder null. entries/bounceRate/ctaRate aus Plausible (Einstiegsseite),
     * registrations: Konten, deren erste Seite dieser Pfad war.
     */
    public record PageRow(String path, long impressions, long clicks, double ctr, double avgPosition,
                          long entries, double bounceRate, double ctaRate, long registrations, String finding) {
    }

    /**
     * finding: STRIKING_DISTANCE, CANNIBALIZED oder null; topPath = Seite mit den meisten Klicks;
     * adwordsCandidate: Non-Brand, schlecht platziert, Zielseite konvertiert organisch gut.
     */
    public record QueryRow(String query, boolean brand, long impressions, long clicks, double ctr, double avgPosition,
                           String topPath, int pageCount, boolean adwordsCandidate, String finding) {
    }

    /**
     * Quelle x Medium x Kampagne aus Plausible; registrations/trials aus der DB nur, wenn zuordenbar
     * (UTM gesetzt oder Google organisch), sonst attributed=false.
     */
    public record CampaignRow(String source, String utmMedium, String utmCampaign, long visitors, long visits,
                              long ctaVisitors, double ctaRate, long registrations, long trials, boolean attributed) {
    }

    /**
     * Modellseite {@code /modelle/:brand/:model} (oder auf Markenebene die Marke samt Markenseite, model = null).
     * cars/logs: Datenbasis aus der DB ohne Seed-Daten, nicht auf den Zeitraum begrenzt. finding: CONTENT_GAP
     * (Nachfrage ohne Daten), INVISIBLE (Daten, aber keine Sichtbarkeit) oder null; auf Markenebene immer null.
     */
    public record ModelRow(String brand, String model, String path, long impressions, long clicks, double ctr,
                           double avgPosition, long registrations, long cars, long logs, String finding) {
    }
}
