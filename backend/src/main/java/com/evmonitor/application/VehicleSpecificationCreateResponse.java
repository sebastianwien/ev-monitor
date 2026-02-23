package com.evmonitor.application;

public record VehicleSpecificationCreateResponse(
        VehicleSpecificationResponse specification,
        Integer coinsAwarded
) {
}
