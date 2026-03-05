package com.evmonitor.application.tesla;

/**
 * Result of a Tesla Fleet API history sync.
 */
public record TeslaFleetSyncResult(
        int logsImported,
        int logsSkipped,
        String vehicleName,
        String message
) {}
