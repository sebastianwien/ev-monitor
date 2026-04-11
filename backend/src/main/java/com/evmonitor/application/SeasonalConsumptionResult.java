package com.evmonitor.application;

import java.math.BigDecimal;

/**
 * Saisonale Verbrauchsaufteilung (Sommer/Winter) für ein Fahrzeug oder eine Modell-Community.
 * Distances in km, consumptions in kWh/100km (null if no data for that season).
 */
public record SeasonalConsumptionResult(
        BigDecimal summerConsumptionKwhPer100km,
        BigDecimal winterConsumptionKwhPer100km,
        BigDecimal totalConsumptionKwhPer100km,
        int summerKm,
        int winterKm,
        int summerLogCount,
        int winterLogCount
) {}
