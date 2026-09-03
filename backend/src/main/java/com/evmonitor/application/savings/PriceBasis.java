package com.evmonitor.application.savings;

import java.math.BigDecimal;

/**
 * Ein Preis samt seiner Herkunft. Die Stichprobengroesse gehoert dazu, weil ein
 * Median aus 5 Ladungen etwas anderes behauptet als einer aus 2.659.
 *
 * @param pricePerKwh null genau dann, wenn {@code source == NONE}
 */
public record PriceBasis(PriceSource source, BigDecimal pricePerKwh, int sampleSize) {

    public static final PriceBasis NONE = new PriceBasis(PriceSource.NONE, null, 0);

    public boolean isKnown() {
        return source != PriceSource.NONE && pricePerKwh != null;
    }
}
