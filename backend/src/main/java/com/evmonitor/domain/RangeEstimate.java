package com.evmonitor.domain;

import java.math.BigDecimal;

/**
 * Reichweite eines Zeitraums, fertig gerechnet nach {@link RangeMath}, mit Basis und Herkunft.
 *
 * @param rangeKm null ohne Kapazität
 * @param scope   bei {@link RangeBasis.Source#PEER_DRIVE} Variante oder Modell, sonst null
 */
public record RangeEstimate(Integer rangeKm, BigDecimal consumptionKwhPer100km, RangeBasis.Source source, PeerDrive.Scope scope) {

    /** Null ohne Basis. */
    public static RangeEstimate of(BigDecimal effectiveCapacityKwh, RangeBasis basis) {
        if (basis == null) return null;
        return new RangeEstimate(RangeMath.rangeKm(effectiveCapacityKwh, basis.consumptionKwhPer100km()),
                basis.consumptionKwhPer100km(), basis.source(), basis.scope());
    }
}
