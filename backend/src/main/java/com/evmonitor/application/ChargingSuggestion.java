package com.evmonitor.application;

import com.evmonitor.domain.ChargingSiteUsage;

import java.util.UUID;

/**
 * Der Vorschlag fuer den Ortsschritt des Wizards: der Nutzer steht an einem Ort, an dem er
 * schon geladen hat. Entweder ein bekannter oeffentlicher Standort (mit der Ladekarte vom
 * letzten Mal, falls es eine gab) oder seine private Zelle - die ergibt sich aus den eigenen
 * Logs, nicht aus einem gespeicherten Heimatort.
 *
 * @param lastProviderId Ladekarte des letzten Logs an diesem Standort, null ohne Karte
 */
public record ChargingSuggestion(Kind kind, ChargingSiteUsage site, UUID lastProviderId) {

    public enum Kind { SITE, PRIVATE }

    public static ChargingSuggestion site(ChargingSiteUsage site, UUID lastProviderId) {
        return new ChargingSuggestion(Kind.SITE, site, lastProviderId);
    }

    public static ChargingSuggestion privateCell() {
        return new ChargingSuggestion(Kind.PRIVATE, null, null);
    }
}
