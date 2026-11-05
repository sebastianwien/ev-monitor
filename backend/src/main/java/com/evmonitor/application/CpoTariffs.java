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
     * @param direct der Betreiber selbst (App oder Ad-hoc), keine fremde Ladekarte
     */
    public record Tariff(String name, String variant, BigDecimal pricePerKwh, BigDecimal monthlyFeeEur,
                         LocalDate verifiedOn, boolean direct) {}
}
