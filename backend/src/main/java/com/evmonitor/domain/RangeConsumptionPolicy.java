package com.evmonitor.domain;

import java.math.BigDecimal;

/**
 * Welcher Verbrauch die Reichweite bestimmt, in vier Stufen, je Zeitraum (4 Wochen, Sommer, Winter)
 * einzeln: eigene Fahrten, Fahrten anderer Fahrer (Variante vor Modell), eigene Ladungen, Ladungen der
 * Fahrer des Modells; erst ohne jede Ladung eine Schätzung aus WLTP mal Flottenfaktor. Reichweite heißt
 * am Stück fahren, darum Fahrten vor Ladungen: der Wert aus Ladungen enthält den Stand. Kein Deckel auf
 * Messwerte: wer misst, zeigt seinen Messwert. Öffentliche Seiten haben keine eigenen Werte und starten
 * bei Stufe 2; ihr Wert ist zugleich der angezeigte Verbrauch des Modells.
 */
public final class RangeConsumptionPolicy {

    /** Grenzen der Schätzung in kWh/100 km. */
    static final BigDecimal ESTIMATE_MIN = BigDecimal.TEN;
    static final BigDecimal ESTIMATE_MAX = new BigDecimal("40");

    private RangeConsumptionPolicy() {}

    /**
     * Stufe 4: Ladungen der Fahrer des Modells, sonst WLTP mal Flottenfaktor.
     *
     * @param peerCharges Verbrauch aus den Ladungen der Fahrer des Modells, sonst null
     * @param wltp        Herstellerangabe des Modells (Variante des Fragenden, sonst Modellschnitt), sonst null
     * @param fleetFactor Flottenfaktor für den Zeitraum ({@code WltpFleetFactors}), sonst null
     */
    public record PeerFallback(BigDecimal peerCharges, BigDecimal wltp, BigDecimal fleetFactor) {
        public static final PeerFallback NONE = new PeerFallback(null, null, null);
    }

    /**
     * @param ownDrive   eigener Fahrverbrauch nach {@link DriveConsumption#kwhPer100km()}, sonst null
     * @param peer       Fahrverbrauch anderer Fahrer, Variante vor Modell, sonst null oder ohne Wert
     * @param ownCharges eigener Verbrauch aus Ladungen im selben Zeitraum, sonst null
     * @param fallback   Stufe 4, nie null (ggf. {@link PeerFallback#NONE})
     * @return null ohne jeden Wert
     */
    public static RangeBasis decide(BigDecimal ownDrive, PeerDrive peer, BigDecimal ownCharges, PeerFallback fallback) {
        if (ownDrive != null) return new RangeBasis(ownDrive, RangeBasis.Source.OWN_DRIVE, null, 0);
        if (peer != null && peer.hasValue()) return new RangeBasis(peer.consumptionKwhPer100km(), RangeBasis.Source.PEER_DRIVE, peer.scope(), peer.users());
        if (ownCharges != null && ownCharges.signum() > 0) return new RangeBasis(ownCharges, RangeBasis.Source.OWN_CHARGES, null, 0);
        if (fallback.peerCharges() != null && fallback.peerCharges().signum() > 0) {
            return new RangeBasis(fallback.peerCharges(), RangeBasis.Source.PEER_CHARGES, null, 0);
        }
        BigDecimal estimate = wltpEstimate(fallback.wltp(), fallback.fleetFactor());
        return estimate == null ? null : new RangeBasis(estimate, RangeBasis.Source.WLTP_ESTIMATE, null, 0);
    }

    /** Öffentliche Seiten: kein Fragender, also keine eigenen Werte. */
    public static RangeBasis decidePublic(PeerDrive peer, BigDecimal peerCharges, BigDecimal wltp, BigDecimal fleetFactor) {
        return decide(null, peer, null, new PeerFallback(peerCharges, wltp, fleetFactor));
    }

    /** WLTP mal Flottenfaktor, auf 10 bis 40 kWh/100 km begrenzt; null ohne beides. */
    public static BigDecimal wltpEstimate(BigDecimal wltp, BigDecimal fleetFactor) {
        if (wltp == null || fleetFactor == null || wltp.signum() <= 0) return null;
        return wltp.multiply(fleetFactor).max(ESTIMATE_MIN).min(ESTIMATE_MAX);
    }
}
