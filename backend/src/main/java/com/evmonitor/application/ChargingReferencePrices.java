package com.evmonitor.application;

import java.math.BigDecimal;

/**
 * Normalized community reference charging prices (EUR/kWh), split by private ("home") and public
 * charging. The model comparison blends these by the buyer's expected home-charging share,
 * so cost differences between models reflect the car - not the owners' charging behaviour.
 * petrol/dieselPricePerLiter: German averages from Tankerkoenig (fallback values without API key),
 * combustionLitersPer100km: the shared combustion-car assumption for the comparison.
 */
public record ChargingReferencePrices(
        BigDecimal homePricePerKwh,
        BigDecimal publicPricePerKwh,
        BigDecimal petrolPricePerLiter,
        BigDecimal dieselPricePerLiter,
        BigDecimal combustionLitersPer100km
) {}
