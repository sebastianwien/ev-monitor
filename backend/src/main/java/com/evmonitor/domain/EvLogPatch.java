package com.evmonitor.domain;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Value object representing a partial update to an EvLog.
 * All fields are nullable — null means "keep existing value".
 */
@Builder
@Getter
public class EvLogPatch {
    private final BigDecimal kwh;
    private final BigDecimal costEur;
    private final Integer durationMin;
    private final String geohash;
    private final Integer odometerKm;
    private final Integer socBefore;
    private final Integer socAfter;
    private final BigDecimal maxChargingPowerKw;
    private final ChargingType chargingType;
    private final RouteType routeType;
    private final TireType tireType;
    private final Boolean isPublicCharging;
    private final String cpoName;
    private final EnergyMeasurementType measurementType;
    private final BigDecimal costExchangeRate;
    private final String costCurrency;
    private final UUID chargingProviderId;
}
