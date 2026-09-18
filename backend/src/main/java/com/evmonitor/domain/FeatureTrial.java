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

    private final TrialWindow window;

    FeatureTrial(LocalDate launchDate, int trialDays) {
        this.window = new TrialWindow(launchDate, trialDays);
    }

    TrialWindow window() {
        return window;
    }
}
