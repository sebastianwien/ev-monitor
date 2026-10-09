package com.evmonitor.domain.rating;

import java.math.BigDecimal;

/** Messwerte zum Zeitpunkt des Einreichens, als Beleg neben dem Text. Verbrauch kann fehlen. */
public record ReviewProof(int kmDriven, int logCount, BigDecimal consumptionKwhPer100km) {
}
