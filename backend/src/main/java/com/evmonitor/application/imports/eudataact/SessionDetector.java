package com.evmonitor.application.imports.eudataact;

import java.util.List;
import java.util.Set;

/**
 * Erkennt Ladevorgaenge in einer Export-Variante. VW liefert je nach Fahrzeugplattform und
 * Anfrageart unterschiedliche Formate; der erste Detektor, der {@link #supports} bejaht, gewinnt.
 * <p>
 * Exporte koennen Hunderte MB gross sein. Deshalb entscheidet ein Detektor zuerst anhand der
 * Feldnamen ({@link #mightSupport}), ob er in Frage kommt, und bekommt danach nur die Eintraege
 * zu sehen, die er per {@link #accepts} anfordert.
 */
interface SessionDetector {

    /** Billige Vorauswahl nur anhand der Feldnamen der Datei. */
    boolean mightSupport(Set<String> fieldNames);

    /** Welche Eintraege beim Einlesen behalten werden sollen. */
    boolean accepts(String field);

    boolean supports(EntryIndex index);

    List<EUDataActSession> detect(EntryIndex index);
}
