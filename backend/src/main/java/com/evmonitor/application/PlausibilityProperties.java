package com.evmonitor.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "evmonitor.consumption.plausibility")
@Component
public class PlausibilityProperties {

    /** Hard lower bound — nothing below this is physically plausible for an EV. */
    private double absoluteMinKwhPer100km = 10.0;

    /** Hard upper bound — nothing above this is physically plausible for an EV. */
    private double absoluteMaxKwhPer100km = 40.0;

    /** WLTP lower factor: consumption must be ≥ WLTP × this (bootstrap fallback). */
    private double wltpLowerFactor = 0.75;

    /** WLTP upper factor: consumption must be ≤ WLTP × this (bootstrap fallback). */
    private double wltpUpperFactor = 2.2;

    /** σ multiplier for statistical check: mean ± sigmaMultiplier × stdDev. */
    private double sigmaMultiplier = 2.5;

    /** Minimum number of valid trips required to use the statistical check (Layer 2a). */
    private int minTripsForStatistical = 5;

    /**
     * Trips shorter than this distance (km) are excluded from per-log consumption calculation
     * and plausibility context. Below ~10km, SoC granularity (±1% on integer-percent telemetry
     * = ±0.7-0.8 kWh) and capacity assumptions distort the kWh/100km result far beyond
     * useful precision — any value displayed would be misleading rather than informative.
     */
    private int minTripDistanceKm = 10;

    /**
     * Ladewirkungsgrad AC ab {@link #acSlowChargingThresholdKw} (Wallbox): Anteil der Energie, der im Akku
     * ankommt. Gemessen an den Brutto/Netto-Paaren der Plattform (Median 0,95 ab 4 kW, 21.10.2026),
     * ADAC 2026 bei 11 kW 0,93 bis 0,95.
     */
    private double acChargingEfficiency = 0.95;

    /**
     * Ladewirkungsgrad AC unter {@link #acSlowChargingThresholdKw}: Steckdose, PV-Überschuss, lange Steckzeit
     * mit Standby-Bezug. Paare: Median 0,87 unter 4 kW.
     */
    private double acChargingEfficiencySlow = 0.87;

    /** Ladewirkungsgrad AC, wenn weder Leistung noch Dauer bekannt sind. Bisheriger globaler Wert. */
    private double acChargingEfficiencyUnknownPower = 0.90;

    /** Grenze zwischen langsamer und normaler AC-Klasse in kW. Die Grenze selbst zählt zur normalen Klasse. */
    private double acSlowChargingThresholdKw = 4.0;

    /**
     * Charging efficiency for DC fast charging: ratio of energy entering battery vs. drawn from charger.
     * Source: ADAC 2025 (~5% loss under normal conditions).
     */
    private double dcChargingEfficiency = 0.95;

    public double getAbsoluteMinKwhPer100km() { return absoluteMinKwhPer100km; }
    public void setAbsoluteMinKwhPer100km(double v) { this.absoluteMinKwhPer100km = v; }

    public double getAbsoluteMaxKwhPer100km() { return absoluteMaxKwhPer100km; }
    public void setAbsoluteMaxKwhPer100km(double v) { this.absoluteMaxKwhPer100km = v; }

    public double getWltpLowerFactor() { return wltpLowerFactor; }
    public void setWltpLowerFactor(double v) { this.wltpLowerFactor = v; }

    public double getWltpUpperFactor() { return wltpUpperFactor; }
    public void setWltpUpperFactor(double v) { this.wltpUpperFactor = v; }

    public double getSigmaMultiplier() { return sigmaMultiplier; }
    public void setSigmaMultiplier(double v) { this.sigmaMultiplier = v; }

    public int getMinTripsForStatistical() { return minTripsForStatistical; }
    public void setMinTripsForStatistical(int v) { this.minTripsForStatistical = v; }

    public int getMinTripDistanceKm() { return minTripDistanceKm; }
    public void setMinTripDistanceKm(int v) { this.minTripDistanceKm = v; }

    public double getAcChargingEfficiency() { return acChargingEfficiency; }
    public void setAcChargingEfficiency(double v) { this.acChargingEfficiency = v; }

    public double getAcChargingEfficiencySlow() { return acChargingEfficiencySlow; }
    public void setAcChargingEfficiencySlow(double v) { this.acChargingEfficiencySlow = v; }

    public double getAcChargingEfficiencyUnknownPower() { return acChargingEfficiencyUnknownPower; }
    public void setAcChargingEfficiencyUnknownPower(double v) { this.acChargingEfficiencyUnknownPower = v; }

    public double getAcSlowChargingThresholdKw() { return acSlowChargingThresholdKw; }
    public void setAcSlowChargingThresholdKw(double v) { this.acSlowChargingThresholdKw = v; }

    /**
     * AC-Ladewirkungsgrad nach geschätzter Ladeleistung: null ergibt den Wert ohne Leistung,
     * unter der Schwelle die langsame Klasse, sonst den Standardwert.
     */
    public double acChargingEfficiencyForPowerKw(java.math.BigDecimal powerKw) {
        if (powerKw == null) return acChargingEfficiencyUnknownPower;
        return powerKw.doubleValue() < acSlowChargingThresholdKw ? acChargingEfficiencySlow : acChargingEfficiency;
    }

    public double getDcChargingEfficiency() { return dcChargingEfficiency; }
    public void setDcChargingEfficiency(double v) { this.dcChargingEfficiency = v; }
}
