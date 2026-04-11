package com.evmonitor.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
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
}
