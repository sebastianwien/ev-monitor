package com.evmonitor.application;

import java.math.BigDecimal;

/**
 * Normalized community reference charging prices (EUR/kWh), split by private ("home") and public
 * charging. The model comparison blends these by the buyer's expected home-charging share,
 * so cost differences between models reflect the car - not the owners' charging behaviour.
 * petrol/dieselPricePerLiter: German averages from Tankerkoenig (fallback values without API key),
 * combustionLitersPer100km: the shared combustion-car assumption for the comparison.
 * fastAdHocPricePerKwh: fast charging without a subscription, the mean of the big networks' contract-free
 * DC prices from the tariff table; null without tariffs. What a new driver pays on the motorway.
 * home/publicChargingEfficiency: battery kWh per plug kWh at home (AC) and on the road (DC), from
 * PlausibilityProperties. Consumption figures are battery side, the price is per plug kWh: every cost
 * figure divides by the efficiency of where the buyer charges. Nullable for Blue/Green.
 */
public record ChargingReferencePrices(
        BigDecimal homePricePerKwh,
        BigDecimal publicPricePerKwh,
        BigDecimal petrolPricePerLiter,
        BigDecimal dieselPricePerLiter,
        BigDecimal combustionLitersPer100km,
        BigDecimal fastAdHocPricePerKwh,
        BigDecimal homeChargingEfficiency,
        BigDecimal publicChargingEfficiency
) {}
