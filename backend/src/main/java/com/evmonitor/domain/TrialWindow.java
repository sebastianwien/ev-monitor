package com.evmonitor.domain;

import java.time.LocalDate;

/**
 * Launch-verankertes Gratis-Fenster: beginnt am spaeteren von Registrierung und
 * {@code launchDate} und traegt {@code days} Tage. Bestandsnutzer bekommen ab dem Launch
 * den vollen Zeitraum, wer spaeter registriert seine Tage ab Registrierung. Die
 * Arithmetik lebt nur hier - {@link FeatureTrial} (fest verdrahtete Kacheln) und
 * konfigurierte Trials teilen sie sich.
 */
public record TrialWindow(LocalDate launchDate, int days) {

    /** Letzter Tag, an dem das Fenster fuer einen an {@code registeredOn} registrierten Nutzer traegt. */
    public LocalDate endFor(LocalDate registeredOn) {
        LocalDate anchor = registeredOn.isAfter(launchDate) ? registeredOn : launchDate;
        return anchor.plusDays(days);
    }
}
