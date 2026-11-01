package com.evmonitor.domain;

import java.math.BigDecimal;

/**
 * Auswahl der Basis für "Wie weit komme ich?". Reichweite heißt am Stück fahren, darum zuerst der
 * eigene Fahrverbrauch. Ohne eigene Fahrten der kleinere Wert aus Modellschnitt und eigenem Verbrauch
 * aus Ladungen: Der Wert aus Ladungen enthält den Stand und liegt fast immer zu hoch. Sonst die Ladungen.
 */
public final class RangeBasisPolicy {

    private RangeBasisPolicy() {}

    /**
     * @param ownDrive        eigener Fahrverbrauch nach {@link DriveConsumption#kwhPer100km()}, sonst null
     * @param modelDrive      Fahrverbrauch anderer Fahrer des Modells nach {@link ModelDriveAverage}, sonst null
     * @param ownCharges      eigener Verbrauch aus Ladungen im selben Zeitraum, sonst null
     * @param driveEnabled    false setzt auf den alten Stand zurück (nur Ladungen), Config-Schalter
     * @return null ohne jeden Wert
     */
    public static RangeBasis decide(BigDecimal ownDrive, BigDecimal modelDrive, BigDecimal ownCharges, boolean driveEnabled) {
        BigDecimal charges = ownCharges != null && ownCharges.signum() > 0 ? ownCharges : null;
        if (driveEnabled) {
            if (ownDrive != null) return new RangeBasis(ownDrive, RangeBasis.Source.OWN_DRIVE);
            if (modelDrive != null && (charges == null || modelDrive.compareTo(charges) <= 0)) {
                return new RangeBasis(modelDrive, RangeBasis.Source.MODEL_DRIVE);
            }
        }
        return charges == null ? null : new RangeBasis(charges, RangeBasis.Source.OWN_CHARGES);
    }
}
