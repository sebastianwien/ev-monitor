package com.evmonitor.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Result of the price suggestion lookup for the log form.
 * Returns the cost per kWh from the most recent log at the same location,
 * plus the charging provider (tariff) that was used there.
 */
public record PriceSuggestion(BigDecimal costPerKwh, UUID chargingProviderId) {
}
