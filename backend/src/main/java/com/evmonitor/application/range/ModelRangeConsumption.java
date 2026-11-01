package com.evmonitor.application.range;

import com.evmonitor.domain.RangeBasis;

/**
 * Verbrauch, mit dem die öffentlichen Seiten (Modellseite, Liste, Rechner, Embed) die Reichweite eines
 * Modells rechnen, nach {@link com.evmonitor.domain.RangeConsumptionPolicy} ohne eigene Werte: Fahrten
 * anderer Fahrer (Stufe 2), sonst Schätzung (Stufe 4). Je Wert null ohne Daten.
 *
 * @param overall alle Jahre
 * @param summer  Mai bis August
 * @param winter  November bis Februar
 */
public record ModelRangeConsumption(RangeBasis overall, RangeBasis summer, RangeBasis winter) {

    public static final ModelRangeConsumption NONE = new ModelRangeConsumption(null, null, null);
}
