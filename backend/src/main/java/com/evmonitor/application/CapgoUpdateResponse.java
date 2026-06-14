package com.evmonitor.application;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Antwort an den @capgo/capacitor-updater.
 *
 * Ist ein Update verfuegbar, sind alle Felder gesetzt. Gibt es kein Update, wird
 * {@link #none()} zurueckgegeben - dank NON_NULL serialisiert das zu "{}" (kein
 * url-Key), was Capgo als "kein Update" interpretiert.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CapgoUpdateResponse(String version, String url, String checksum) {

    public static CapgoUpdateResponse none() {
        return new CapgoUpdateResponse(null, null, null);
    }
}
