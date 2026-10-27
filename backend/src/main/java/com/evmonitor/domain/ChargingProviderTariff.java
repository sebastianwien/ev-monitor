package com.evmonitor.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
@Builder(toBuilder = true)
public class ChargingProviderTariff {

    private final UUID id;
    private final String empName;
    private final String tariffVariant;
    private final String cpoName;
    private final String priceTier;
    private final ChargingType chargingType;
    private final BigDecimal pricePerKwh;
    private final BigDecimal sessionFeeEur;
    private final BigDecimal monthlyFeeEur;
    private final BigDecimal blockingFeePerMin;
    private final Integer blockingFeeAfterMin;
    private final boolean dynamicPricing;
    private final LocalDate validFrom;
    private final LocalDate validUntil;
    private final String sourceUrl;
    private final LocalDateTime lastVerifiedAt;
    /** Gilt an allen Betreibern, für die der Anbieter keinen genaueren Preis hat. */
    private final boolean appliesElsewhere;
    /** Untere Leistungsgrenze in kW, ausschließlich; null = ab 0 kW. */
    private final Integer minPowerKw;
    /** Höchstbetrag der Blockiergebühr je Ladung; null = kein Deckel bekannt. */
    private final BigDecimal blockingFeeCapEur;
}
