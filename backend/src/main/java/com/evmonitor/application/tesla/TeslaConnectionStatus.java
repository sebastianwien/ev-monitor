package com.evmonitor.application.tesla;

import java.time.LocalDateTime;

public record TeslaConnectionStatus(
    boolean connected,
    String vehicleName,
    LocalDateTime lastSyncAt,
    boolean autoImportEnabled
) {}
