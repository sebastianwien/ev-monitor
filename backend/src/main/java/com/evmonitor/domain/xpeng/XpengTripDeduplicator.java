package com.evmonitor.domain.xpeng;

import com.evmonitor.domain.DataSource;
import com.evmonitor.domain.EvTrip;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

/**
 * Zweiter, stabiler Dedup-Weg fuer XPeng-Trips.
 *
 * <p>Der primaere Dedup laeuft ueber eine deterministische {@code externalId}
 * aus {@code VIN@startedAt}. Der Haken: {@code startedAt} wird vom Trip-Detektor
 * <em>berechnet</em>. Aendert sich die Detektor-Logik, verschiebt sich die
 * Startsekunde und derselbe physische Trip bekaeme beim Re-Import eine andere
 * externalId - also ein Duplikat.
 *
 * <p>Dieser Guard erkennt einen bereits importierten Trip an etwas, das nicht
 * aus unserer Berechnung stammt: dem <b>Kilometerstand am Start</b> (kommt direkt
 * vom Fahrzeug). Eine enge Zeit-Toleranz verhindert, dass zwei echte Kurztrips am
 * selben gerundeten Kilometerstand faelschlich verschmelzen.
 */
public final class XpengTripDeduplicator {

    /** Wie weit der Start-Kilometerstand abweichen darf - deckt eine um 1-2 Samples
     *  verschobene Start-Erkennung ab, ohne getrennte Trips zu verschmelzen. */
    static final BigDecimal ODO_TOLERANCE_KM = new BigDecimal("0.3");
    /** Eine Detektor-Verschiebung liegt im Sekundenbereich; 5 min sind grosszuegig
     *  und trennen dennoch aufeinanderfolgende echte Trips. */
    static final long TIME_TOLERANCE_SECONDS = 300;

    private XpengTripDeduplicator() {}

    /**
     * @param existingTrips bereits gespeicherte Trips des Fahrzeugs im Zeitfenster
     *                      um {@code candidate.startedAt()} (nicht geloescht).
     * @return {@code true} wenn {@code candidate} bereits als XPeng-Import vorliegt.
     */
    public static boolean isAlreadyImported(List<EvTrip> existingTrips, DetectedTrip candidate) {
        if (candidate == null || candidate.startedAt() == null || candidate.odometerStartKm() == null) {
            return false;
        }
        if (existingTrips == null || existingTrips.isEmpty()) return false;

        for (EvTrip e : existingTrips) {
            if (!DataSource.XPENG_IMPORT.name().equals(e.getDataSource())) continue;
            if (e.getOdometerStartKm() == null || e.getTripStartedAt() == null) continue;

            BigDecimal odoDiff = e.getOdometerStartKm().subtract(candidate.odometerStartKm()).abs();
            if (odoDiff.compareTo(ODO_TOLERANCE_KM) > 0) continue;

            long secDiff = Math.abs(Duration.between(
                    e.getTripStartedAt().toLocalDateTime(), candidate.startedAt()).toSeconds());
            if (secDiff <= TIME_TOLERANCE_SECONDS) return true;
        }
        return false;
    }
}
