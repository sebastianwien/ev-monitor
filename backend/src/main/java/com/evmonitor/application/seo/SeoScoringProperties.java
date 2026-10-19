package com.evmonitor.application.seo;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Schwellen für die Befunde im SEO-Tab. Defaults sind SEO-Allgemeinwissen, keine Daten; wer sie anpasst,
 * tut das per Property, nicht im Code.
 */
@ConfigurationProperties(prefix = "seo.scoring")
@Component
@Getter
@Setter
public class SeoScoringProperties {

    /** Unter so vielen Impressionen im Zeitraum gibt es keinen Befund: Longtail-Rauschen. */
    private long minImpressions = 10;

    /** "Unterschätzt": Seite rankt sichtbar, bekommt aber unterdurchschnittlich Klicks. */
    private double underratedMinPosition = 4.0;
    private double underratedMaxPosition = 15.0;

    /** "Striking Distance": Suchbegriff knapp hinter der ersten Seite. */
    private double strikingMinPosition = 5.0;
    private double strikingMaxPosition = 20.0;

    /** "Tot": Seite hatte in diesem Rückblick Impressionen, im Zeitraum keine mehr. */
    private int deadLookbackDays = 90;

    /** Zeilenobergrenze je Tabelle im Tab. */
    private int maxRows = 500;
}
