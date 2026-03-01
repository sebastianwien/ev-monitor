package com.evmonitor.application.tesla;

public record TeslaSyncResult(
    int logsImported,
    String vehicleName,
    Integer batteryLevel
) {}
