package com.evmonitor.application;

import com.evmonitor.domain.ChargingType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Gepflegte Tarife, die heute an einem Betreiber gelten - die Grundlage für "Was hätte ich hier
 * mit einer anderen Karte gezahlt?". Nur Katalog, nie Fahrerdaten.
 *
 * @param tariffs günstigste zuerst
 */
public record CpoTariffs(String cpoName, ChargingType chargingType, List<Tariff> tariffs) {

    /**
     * @param direct              der Betreiber selbst (App oder Ad-hoc), keine fremde Ladekarte
     * @param sessionFeeEur       Startgebühr je Ladevorgang, kann fehlen
     * @param blockingFeePerMin   Blockiergebühr je Minute, kann fehlen
     * @param blockingFeeAfterMin ab dieser Minute fällt die Blockiergebühr an
     * @param blockingFeeCapEur   Deckel der Blockiergebühr je Ladevorgang, kann fehlen
     */
    public record Tariff(String name, String variant, BigDecimal pricePerKwh, BigDecimal monthlyFeeEur,
                         LocalDate verifiedOn, boolean direct,
                         BigDecimal sessionFeeEur, BigDecimal blockingFeePerMin, Integer blockingFeeAfterMin,
                         BigDecimal blockingFeeCapEur) {}
}
