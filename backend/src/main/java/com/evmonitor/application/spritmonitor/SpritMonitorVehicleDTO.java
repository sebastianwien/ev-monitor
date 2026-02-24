package com.evmonitor.application.spritmonitor;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SpritMonitorVehicleDTO(
    Integer id,
    String make,
    String model,
    @JsonProperty("maintanktype")
    Integer mainTankType
) {}
