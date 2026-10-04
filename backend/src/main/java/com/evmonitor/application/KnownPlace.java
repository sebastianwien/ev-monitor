package com.evmonitor.application;

import com.evmonitor.domain.ChargingSite;
import com.evmonitor.domain.KnownCell;

/**
 * Ein Ort, an dem der Nutzer schon geladen hat, fuer den Ortsschritt des Wizards: die Zelle aus
 * den eigenen Logs, dazu die Register-Saeule (falls eine verknuepft war) oder der Ortsteil aus
 * dem Geocoder, damit ein Ort ohne Namen wiedererkennbar bleibt.
 *
 * @param placeName Ortsteil, Dorf oder Stadt der Zellmitte; null wenn der Geocoder nichts lieferte,
 *                  eine Saeule den Namen stellt oder der Ort ohnehin "hier" ist
 * @param here      die Position des Nutzers liegt in dieser Zelle
 */
public record KnownPlace(KnownCell cell, ChargingSite site, String placeName, boolean here) {}
