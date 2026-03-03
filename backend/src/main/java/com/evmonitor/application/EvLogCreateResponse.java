package com.evmonitor.application;

public record EvLogCreateResponse(
        EvLogResponse log,
        int coinsAwarded
) {
}
