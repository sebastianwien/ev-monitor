package com.evmonitor.application.spritmonitor;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public record SpritMonitorFuelingDTO(
    String date,
    BigDecimal quantity, // kWh
    BigDecimal cost, // EUR
    @JsonProperty("charging_duration")
    Integer chargingDuration, // minutes
    Position position,
    String stationname,
    String note
) {
    public record Position(
        BigDecimal lat,
        BigDecimal lon
    ) {}
}
