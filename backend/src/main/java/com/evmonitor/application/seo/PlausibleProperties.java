package com.evmonitor.application.seo;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/** Nächtlicher Plausible-Sync (Stats API v2). Der Key kommt aus {@code plausible.api-key}; leer = Sync aus. */
@ConfigurationProperties(prefix = "seo.plausible")
@Component
@Getter
@Setter
public class PlausibleProperties {

    /** Goals, deren Besucher je Einstiegsseite und je Quelle gespeichert werden. Alle Namen sind öffentlich (analytics.ts). */
    private List<String> goals = List.of("cta_register_clicked", "gate_code_verified", "registration_completed",
            "demo_session_started", "import_completed");

    /** Erster Tag mit Plausible-Daten; der Backfill auf leerer Tabelle beginnt hier. */
    private LocalDate backfillFrom = LocalDate.of(2026, 3, 1);

    /** Wie bei GSC: 16 Monate, älteres wird nach dem Sync gelöscht. */
    private int retentionDays = 490;

    /** Tage je API-Fenster; ein Fenster ist ein Call je Abfrageart. */
    private int windowDays = 30;
}
