package com.evmonitor.application;

import java.math.BigDecimal;

/**
 * Normalized community reference charging prices (EUR/kWh), split by private ("home") and public
 * charging. The model comparison slider blends these by the buyer's expected home-charging share,
 * so cost differences between models reflect the car - not the owners' charging behaviour.
 */
public record ChargingReferencePrices(BigDecimal homePricePerKwh, BigDecimal publicPricePerKwh) {}
