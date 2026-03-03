package com.evmonitor.application;

public record CarCreateResponse(
        CarResponse car,
        int coinsAwarded
) {
}
