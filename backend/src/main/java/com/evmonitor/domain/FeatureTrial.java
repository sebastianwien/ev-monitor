package com.evmonitor.domain;

import java.time.LocalDate;

/**
 * Zeitgesteuerte Gratis-Fenster fuer Dashboard-Kacheln.
 *
 * Jede getriggerte Kachel bringt genau eine Zeile mit: ab wann sie startet und wie lange
 * das Trial laeuft. Das Fenster ist launch-verankert - es beginnt am spaeteren von
 * Registrierung und {@link #launchDate} und laeuft dann {@link #trialDays} Tage. Damit
 * bekommt jeder Bestandsnutzer ab dem Launch einen vollen Zeitraum, wer spaeter
 * registriert seine Tage ab Registrierung. Ausgewertet wird ueber
 * {@link User#isWithinTrial(FeatureTrial, LocalDate)} - der Gate der jeweiligen Kachel
 * bleibt die Sicherheitsgrenze.
 *
 * <p>Eine neue getriggerte Kachel ist eine Enum-Zeile plus ein {@code isWithinTrial}-Aufruf
 * im zugehoerigen Gate - keine neue Datums-Arithmetik.
 */
public enum FeatureTrial {
    /** Heimlade-Ersparnis-Kachel ({@link User#canViewChargingSavings()}). */
    HOME_CHARGING_SAVINGS(LocalDate.of(2026, 9, 3), 30);

    private final LocalDate launchDate;
    private final int trialDays;

    FeatureTrial(LocalDate launchDate, int trialDays) {
        this.launchDate = launchDate;
        this.trialDays = trialDays;
    }

    /** Letzter Tag, an dem das Trial fuer einen an {@code registeredOn} registrierten Nutzer traegt. */
    LocalDate endFor(LocalDate registeredOn) {
        LocalDate anchor = registeredOn.isAfter(launchDate) ? registeredOn : launchDate;
        return anchor.plusDays(trialDays);
    }
}
