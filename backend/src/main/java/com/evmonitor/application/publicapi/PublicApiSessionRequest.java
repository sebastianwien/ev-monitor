package com.evmonitor.application.publicapi;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;
import java.util.UUID;

public record PublicApiSessionRequest(
        @NotNull @JsonProperty("car_id") UUID carId,
        @NotNull @NotEmpty @Size(max = 100) @Valid List<SessionEntry> sessions
) {
    public record SessionEntry(
            @Schema(description = "Timestamp of the charging session. ISO 8601 with or without offset, e.g. `2025-08-31T15:07:14+02:00` or `2025-08-31 15:07:14`.")
            @NotNull @JsonProperty("date") String date,

            @Schema(description = "Energy charged in kWh.")
            @NotNull @DecimalMin("0.0") @DecimalMax("10000.0") @JsonProperty("kwh") Double kwh,

            @Schema(description = "Odometer reading in km at the time of charging.")
            @Min(0) @Max(2_000_000) @JsonProperty("odometer_km") Integer odometerKm,

            @Schema(description = "State of Charge before charging in percent (0-100).")
            @Min(0) @Max(100) @JsonProperty("soc_before") Integer socBefore,

            @Schema(description = "State of Charge after charging in percent (0-100).")
            @Min(0) @Max(100) @JsonProperty("soc_after") Integer socAfter,

            @Schema(description = "Total cost of the charging session in EUR.")
            @DecimalMin("0.0") @DecimalMax("10000.0") @JsonProperty("cost_eur") Double costEur,

            @Schema(description = "Duration of the charging session in minutes.")
            @Min(0) @Max(100_000) @JsonProperty("duration_min") Integer durationMin,

            @Schema(description = "Location as `lat lon` or `lat,lon`, e.g. `48.2082 16.3738`. Stored as geohash (6 chars ~600m for private, 7 chars ~150m for public chargers).")
            @Size(max = 50) @JsonProperty("location") String location,

            @Schema(description = "Charging current type. Allowed values: `AC`, `DC`, `UNKNOWN`.", allowableValues = {"AC", "DC", "UNKNOWN"})
            @Size(max = 10) @JsonProperty("charging_type") String chargingType,

            @Schema(description = "Maximum charging power in kW.")
            @DecimalMin("0.0") @DecimalMax("1000.0") @JsonProperty("max_charging_power_kw") Double maxChargingPowerKw,

            @Schema(description = "Route type driven before this charge. Allowed values: `CITY`, `COMBINED`, `HIGHWAY`.", allowableValues = {"CITY", "COMBINED", "HIGHWAY"})
            @Size(max = 10) @JsonProperty("route_type") String routeType,

            @Schema(description = "Tire type used. Allowed values: `SUMMER`, `ALL_YEAR`, `WINTER`.", allowableValues = {"SUMMER", "ALL_YEAR", "WINTER"})
            @Size(max = 10) @JsonProperty("tire_type") String tireType,

            @Schema(description = "Raw source data for reference (e.g. original JSON from import). Not processed.")
            @Size(max = 2000) @JsonProperty("raw_import_data") String rawImportData,

            @Schema(description = "Whether this session was at a public charger (CPO). Set to `true` for any non-home charging. Affects geohash precision (7 chars instead of 5).")
            @JsonProperty("is_public_charging") Boolean isPublicCharging,

            @Schema(description = "CPO (Charge Point Operator) name. Use canonical names from `GET /api/v1/charging-providers`. Unknown values are accepted and stored as-is.")
            @Size(max = 100) @JsonProperty("cpo_name") String cpoName,

            @Schema(description = "At which point energy is measured. `AT_CHARGER` (default): gross energy at the wallbox/charger, ~7% higher than battery entry. `AT_VEHICLE`: net energy entering the battery (e.g. from vehicle APIs). `DRIVING_ONLY`: drive consumption only, excludes standby/preconditioning. If omitted, `AT_CHARGER` is assumed.", allowableValues = {"AT_CHARGER", "AT_VEHICLE", "DRIVING_ONLY"})
            @Size(max = 20) @JsonProperty("measurement_type") String measurementType
    ) {}
}
