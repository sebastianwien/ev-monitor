package com.evmonitor.application.spritmonitor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SpritMonitorFuelingDTO(
    String date,
    BigDecimal quantity,
    @JsonProperty("quantityunitid")
    Integer quantityUnitId, // 5 = kWh
    BigDecimal odometer,
    BigDecimal cost,
    @JsonProperty("charging_duration")
    Integer chargingDuration,
    BigDecimal percent,          // SoC after charging in % (0-100), EV only
    @JsonProperty("charging_power")
    BigDecimal chargingPower,    // max charging power in kW, EV only
    Position position,
    String stationname,
    String note,
    @JsonProperty("charge_info")
    String chargeInfo    // Free-text field that may contain "AC", "DC", connector types, etc.
) {
    private static final int UNIT_KWH = 5;

    public boolean isKwh() {
        return quantityUnitId != null && quantityUnitId == UNIT_KWH;
    }

    /**
     * Parses charge_info for AC/DC. The field is comma-separated (e.g., "AC, Type 2, 11 kW")
     * and may contain connector types or power levels. We split by comma and search for
     * standalone "AC" or "DC" tokens to avoid false positives (e.g., "ADVANCE", "REDCAR").
     * "DC" takes precedence since a false positive there is worse for data quality.
     */
    public com.evmonitor.domain.ChargingType parseChargingType() {
        if (chargeInfo == null || chargeInfo.isBlank()) {
            return com.evmonitor.domain.ChargingType.UNKNOWN;
        }

        // Split by comma and trim each token
        String[] tokens = chargeInfo.split(",");
        for (String token : tokens) {
            String trimmed = token.trim().toUpperCase();
            // Check for exact match or starts with (e.g., "DC 50kW" or "AC 11kW")
            if (trimmed.equals("DC") || trimmed.startsWith("DC ")) {
                return com.evmonitor.domain.ChargingType.DC;
            }
        }

        // Second pass for AC (lower priority to avoid false positives)
        for (String token : tokens) {
            String trimmed = token.trim().toUpperCase();
            if (trimmed.equals("AC") || trimmed.startsWith("AC ")) {
                return com.evmonitor.domain.ChargingType.AC;
            }
        }

        return com.evmonitor.domain.ChargingType.UNKNOWN;
    }

    public record Position(
        BigDecimal lat,
        BigDecimal lon
    ) {
        // SpritMonitor sends position as "lat,lon" string (e.g., "51.194004,6.813039")
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public static Position fromString(String value) {
            if (value == null || value.isBlank()) return null;
            String[] parts = value.split(",", 2);
            if (parts.length != 2) return null;
            try {
                return new Position(new BigDecimal(parts[0].trim()), new BigDecimal(parts[1].trim()));
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }
}
