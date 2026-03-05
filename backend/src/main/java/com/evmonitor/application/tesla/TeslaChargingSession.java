package com.evmonitor.application.tesla;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Single charging session from Tesla Fleet API charging_history endpoint.
 */
public record TeslaChargingSession(
        @JsonProperty("vin") String vin,
        @JsonProperty("chargeStartDateTime") String chargeStartDateTime,
        @JsonProperty("chargeStopDateTime") String chargeStopDateTime,
        @JsonProperty("energyAdded") Double energyAdded,          // kWh
        @JsonProperty("lat") Double lat,
        @JsonProperty("lon") Double lon,
        @JsonProperty("totalDollars") Double totalDollars,         // cost (USD, for Supercharger)
        @JsonProperty("siteType") String siteType,                 // "SUPERCHARGER", "HOME" etc
        @JsonProperty("superchargerName") String superchargerName
) {}
