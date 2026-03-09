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

    /** Trips shorter than this distance (km) are excluded from plausibility context. */
    private int minTripDistanceKm = 20;

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
}
