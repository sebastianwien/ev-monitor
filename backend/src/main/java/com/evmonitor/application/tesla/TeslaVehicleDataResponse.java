package com.evmonitor.application.tesla;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Tesla API vehicle_data response structure
 * Based on Tesla Owner API documentation
 */
public record TeslaVehicleDataResponse(
    @JsonProperty("id") Long id,
    @JsonProperty("vehicle_id") Long vehicleId,
    @JsonProperty("display_name") String displayName,
    @JsonProperty("state") String state,
    @JsonProperty("vin") String vin,
    @JsonProperty("charge_state") ChargeState chargeState,
    @JsonProperty("drive_state") DriveState driveState,
    @JsonProperty("vehicle_state") VehicleState vehicleState,
    @JsonProperty("vehicle_config") VehicleConfig vehicleConfig
) {

    public record ChargeState(
        @JsonProperty("battery_level") Integer batteryLevel,
        @JsonProperty("battery_range") Double batteryRange,
        @JsonProperty("charge_energy_added") Double chargeEnergyAdded,
        @JsonProperty("charging_state") String chargingState,
        @JsonProperty("charger_power") Integer chargerPower,
        @JsonProperty("time_to_full_charge") Double timeToFullCharge,
        @JsonProperty("charge_limit_soc") Integer chargeLimitSoc,
        @JsonProperty("charge_miles_added_rated") Double chargeMilesAddedRated,
        @JsonProperty("timestamp") Long timestamp
    ) {}

    public record DriveState(
        @JsonProperty("latitude") Double latitude,
        @JsonProperty("longitude") Double longitude,
        @JsonProperty("native_latitude") Double nativeLatitude,
        @JsonProperty("native_longitude") Double nativeLongitude,
        @JsonProperty("heading") Integer heading,
        @JsonProperty("gps_as_of") Long gpsAsOf,
        @JsonProperty("timestamp") Long timestamp
    ) {}

    public record VehicleState(
        @JsonProperty("odometer") Double odometer,
        @JsonProperty("vehicle_name") String vehicleName,
        @JsonProperty("timestamp") Long timestamp
    ) {}

    public record VehicleConfig(
        @JsonProperty("car_type") String carType,
        @JsonProperty("trim_badging") String trimBadging,
        @JsonProperty("exterior_color") String exteriorColor
    ) {}
}
