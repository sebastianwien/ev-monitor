package com.evmonitor.application.tesla;

/**
 * Response containing the Tesla OAuth2 authorization URL for the frontend to redirect to.
 */
public record TeslaFleetAuthStartResponse(
        String authUrl,
        boolean fleetApiConfigured
) {}
