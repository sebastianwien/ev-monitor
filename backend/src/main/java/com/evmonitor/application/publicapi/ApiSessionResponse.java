package com.evmonitor.application.publicapi;

import com.evmonitor.domain.EvLog;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Schema(description = "A charging session imported via the Public API")
public record ApiSessionResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("car_id") UUID carId,
        @JsonProperty("data_source") String dataSource,
        @JsonProperty("date") String date,
        @JsonProperty("kwh") Double kwh,
        @JsonProperty("cost_eur") Double costEur,
        @JsonProperty("duration_min") Integer durationMin,
        @JsonProperty("odometer_km") Integer odometerKm,
        @JsonProperty("soc_before") BigDecimal socBefore,
        @JsonProperty("soc_after") BigDecimal socAfter,
        @JsonProperty("max_charging_power_kw") Double maxChargingPowerKw,
        @JsonProperty("charging_type") String chargingType,
        @JsonProperty("route_type") String routeType,
        @JsonProperty("tire_type") String tireType,
        @JsonProperty("kwh_at_vehicle") Double kwhAtVehicle,
        @JsonProperty("temperature_celsius") Double temperatureCelsius,
        /** Bewusst primitiv: die oeffentliche API bleibt zweiwertig. Intern ist der
         *  Ladeort seit V166 dreiwertig, unbekannt wird hier zu false - so wie sich
         *  das Feld vor V166 verhalten hat. Ein plotzliches null wuerde fremde
         *  Clients brechen, die einen Boolean parsen. */
        @JsonProperty("is_public_charging") boolean isPublicCharging,
        @JsonProperty("cpo_name") String cpoName,
        @JsonProperty("measurement_type") String measurementType
) {
    public static ApiSessionResponse fromEvLog(EvLog log) {
        return new ApiSessionResponse(
                log.getId(),
                log.getCarId(),
                log.getDataSource() != null ? log.getDataSource().name() : null,
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
                log.getKwhAtVehicle() != null ? log.getKwhAtVehicle().doubleValue() : null,
                log.getTemperatureCelsius(),
                log.isPublicChargingConfirmed(),
                log.getCpoName(),
                log.getMeasurementType() != null ? log.getMeasurementType().name() : null
        );
    }
}
