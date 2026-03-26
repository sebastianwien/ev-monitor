package com.evmonitor.application.publicapi;

import com.evmonitor.domain.EvLog;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Schema(description = "A charging session imported via the Public API")
public record ApiSessionResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("date") String date,
        @JsonProperty("kwh") Double kwh,
        @JsonProperty("cost_eur") Double costEur,
        @JsonProperty("duration_min") Integer durationMin,
        @JsonProperty("odometer_km") Integer odometerKm,
        @JsonProperty("soc_before") Integer socBefore,
        @JsonProperty("soc_after") Integer socAfter,
        @JsonProperty("max_charging_power_kw") Double maxChargingPowerKw,
        @JsonProperty("charging_type") String chargingType,
        @JsonProperty("route_type") String routeType,
        @JsonProperty("tire_type") String tireType,
        @JsonProperty("is_public_charging") boolean isPublicCharging,
        @JsonProperty("cpo_name") String cpoName,
        @JsonProperty("measurement_type") String measurementType
) {
    public static ApiSessionResponse fromEvLog(EvLog log) {
        return new ApiSessionResponse(
                log.getId(),
                log.getLoggedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                log.getKwhCharged() != null ? log.getKwhCharged().doubleValue() : null,
                log.getCostEur() != null ? log.getCostEur().doubleValue() : null,
                log.getChargeDurationMinutes(),
                log.getOdometerKm(),
                log.getSocBeforeChargePercent(),
                log.getSocAfterChargePercent(),
                log.getMaxChargingPowerKw() != null ? log.getMaxChargingPowerKw().doubleValue() : null,
                log.getChargingType() != null ? log.getChargingType().name() : null,
                log.getRouteType() != null ? log.getRouteType().name() : null,
                log.getTireType() != null ? log.getTireType().name() : null,
                log.isPublicCharging(),
                log.getCpoName(),
                log.getMeasurementType() != null ? log.getMeasurementType().name() : null
        );
    }
}
