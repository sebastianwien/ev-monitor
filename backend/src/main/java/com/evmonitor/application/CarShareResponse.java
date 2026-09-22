package com.evmonitor.application;

/**
 * Antwort auf das Teilen einer Fahrzeugseite.
 *
 * @param token     Zufalls-Token der oeffentlichen URL
 * @param url       fertige absolute URL der Fahrzeugseite, inkl. Empfehlungscode des Teilenden
 * @param bannerUrl absolute URL des Forum-Signatur-Banners (468x60 PNG). Kommt vom Server,
 *                  damit Web, iOS und Android dieselbe in die Signatur schreiben
 */
public record CarShareResponse(String token, String url, String bannerUrl) {}
