package com.evmonitor.application.spritmonitor;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public record SpritMonitorFuelingDTO(
    String date,
    BigDecimal quantity,
    @JsonProperty("quantityunitid")
    Integer quantityUnitId, // 5 = kWh
    BigDecimal odometer,
    BigDecimal cost,
    @JsonProperty("charging_duration")
    Integer chargingDuration,
    Position position,
    String stationname,
    String note
) {
    private static final int UNIT_KWH = 5;

    public boolean isKwh() {
        return quantityUnitId != null && quantityUnitId == UNIT_KWH;
    }

    public record Position(
        BigDecimal lat,
        BigDecimal lon
    ) {}
}
