package com.evmonitor.application;

import com.evmonitor.domain.ChargingSite;
import com.evmonitor.domain.KnownCell;

/**
 * Ein Ort, an dem der Nutzer schon geladen hat, fuer den Ortsschritt des Wizards: die Zelle aus
 * den eigenen Logs, dazu die Register-Saeule (falls eine verknuepft war) oder der Ortsteil aus
 * dem Geocoder, damit ein Ort ohne Namen wiedererkennbar bleibt.
 *
 * @param placeName      Ortsteil, Dorf oder Stadt der Zellmitte; null wenn der Geocoder nichts lieferte
 *                       oder eine Saeule den Namen stellt
 * @param distanceMeters Entfernung der Position zur Zellmitte, null ohne Position
 * @param here           ob die Position in dieser Zelle liegt (Saeule und oeffentliche Zelle 300 m, private 800 m)
 */
public record KnownPlace(KnownCell cell, ChargingSite site, String placeName, Integer distanceMeters, boolean here) {

    public KnownPlace withPosition(int distanceMeters, boolean here) {
        return new KnownPlace(cell, site, placeName, distanceMeters, here);
    }
}
