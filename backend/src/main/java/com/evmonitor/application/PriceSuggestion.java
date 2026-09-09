package com.evmonitor.application;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Result of the price suggestion lookup for the log form and the price-amend modal.
 * Cost per kWh of the most recent priced charge at the same location, the card used there,
 * and when that anchor charge happened - so the UI can show where the number comes from.
 */
public record PriceSuggestion(BigDecimal costPerKwh, UUID chargingProviderId, LocalDateTime anchorLoggedAt) {
}
