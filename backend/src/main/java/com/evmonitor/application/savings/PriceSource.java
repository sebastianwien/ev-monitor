package com.evmonitor.application.savings;

/**
 * Woher ein Preis stammt. Die Kachel benennt die Stufe, damit der Nutzer die Zahl
 * einordnen kann - und sieht, dass eigenes Loggen sie schaerfer macht.
 */
public enum PriceSource {
    /** Gewichteter Durchschnitt der eigenen bepreisten Heimladungen. */
    OWN_LOGS,
    /** Median der eigenen bepreisten oeffentlichen Ladungen. */
    OWN_PUBLIC,
    /** Median oeffentlicher Ladungen in der Umgebung. */
    REGION,
    /** Median oeffentlicher Ladungen im Land des Nutzers. */
    COUNTRY,
    /** Keine Quelle - es wird nicht geschaetzt. */
    NONE
}
