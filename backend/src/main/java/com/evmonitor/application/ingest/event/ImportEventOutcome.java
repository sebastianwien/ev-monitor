package com.evmonitor.application.ingest.event;

/** Ergebnis eines Imports im Protokoll {@code import_event}. */
public enum ImportEventOutcome {
    /** Mindestens ein Eintrag neu angelegt, keine Fehler. */
    IMPORTED,
    /** Nichts neu, keine Fehler: alles schon vorhanden (auch gelöscht) oder leer. */
    NO_NEW_DATA,
    /** Teils angelegt, einzelne Einträge gescheitert. */
    PARTIAL,
    /** Nichts angelegt wegen eines Fehlers, auch: Transaktion zurückgerollt. */
    FAILED,
    /** Abgelehnt, bevor etwas geschrieben wurde: Auto unbekannt oder fremd, Pflichtfeld fehlt. */
    REJECTED,
    /** Datei oder Lieferung nicht lesbar, das Gateway wurde gar nicht erst gerufen. */
    PARSE_ERROR;

    /** Ergebnis aus den Zählern eines Aufrufs; Duplikate zählen nicht als Fehler. */
    public static ImportEventOutcome of(int imported, int failed) {
        if (failed == 0) return imported > 0 ? IMPORTED : NO_NEW_DATA;
        return imported > 0 ? PARTIAL : FAILED;
    }

    /** Hat angelegt oder hatte nichts Neues - zählt als Lebenszeichen der Quelle. */
    public boolean isSuccess() {
        return this == IMPORTED || this == NO_NEW_DATA || this == PARTIAL;
    }
}
