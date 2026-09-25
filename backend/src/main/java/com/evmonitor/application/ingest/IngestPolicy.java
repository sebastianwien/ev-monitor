package com.evmonitor.application.ingest;

import com.evmonitor.application.CoinLogService.CoinEvent;

import java.time.Duration;

/**
 * Regeln, mit denen das {@link IngestGateway} Ladungen einer Quelle über eine Tür annimmt.
 * Jedes Feld ist ein gemessener Unterschied der alten Türen; die Werte stehen in {@link IngestPolicies}.
 *
 * @param dedupWindow               Toleranz um die Minute beim Duplikat-Check je Quelle; 0 = minutengenau
 * @param bumpSameTimestampInBatch  gleiche Zeitstempel im Batch je 10 Minuten versetzen (Tagesdatum-Importe)
 * @param inheritTireAndRouteType   Reifen und Strecke vom letzten Log davor übernehmen (Live-Daten ohne diese Angaben)
 * @param coinEvent                 Watt je angelegter Ladung, {@code null} = keine
 * @param kwhIsVehicleSide          die Quelle meldet im kWh-Feld fahrzeugseitige Energie (Tronity)
 * @param isolateEntryErrors        ein fehlerhafter Eintrag zählt als Fehler statt den Aufruf abzubrechen
 * @param sohEventWithoutVehicleKwh SoH-Erkennung auch ohne fahrzeugseitige kWh anstoßen
 */
public record IngestPolicy(
        Duration dedupWindow,
        boolean bumpSameTimestampInBatch,
        boolean inheritTireAndRouteType,
        CoinEvent coinEvent,
        boolean kwhIsVehicleSide,
        boolean isolateEntryErrors,
        boolean sohEventWithoutVehicleKwh) {
}
