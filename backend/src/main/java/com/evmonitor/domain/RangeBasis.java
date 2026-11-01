package com.evmonitor.domain;

import java.math.BigDecimal;

/**
 * Verbrauch, mit dem die Reichweite rechnet, und woher er kommt. Das Frontend wählt nach {@code source}
 * nur noch den Herkunftssatz.
 */
public record RangeBasis(BigDecimal consumptionKwhPer100km, Source source) {

    public enum Source {
        /** Eigene Fahrten ab 20 km im Zeitraum. */
        OWN_DRIVE,
        /** Fahrverbrauch anderer Fahrer desselben Modells. */
        MODEL_DRIVE,
        /** Eigener Verbrauch aus Ladungen, enthält den Stand. */
        OWN_CHARGES
    }
}
