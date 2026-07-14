package com.evmonitor.application.imports.eudataact;

import java.util.List;

/**
 * Erkennt Ladevorgaenge in einer Export-Variante. VW liefert je nach Fahrzeugplattform
 * unterschiedliche Formate; der erste Detektor, der {@link #supports} bejaht, gewinnt.
 */
interface SessionDetector {

    boolean supports(EntryIndex index);

    List<EUDataActSession> detect(EntryIndex index);
}
