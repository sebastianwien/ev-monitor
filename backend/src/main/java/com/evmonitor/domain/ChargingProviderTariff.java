package com.evmonitor.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

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
    private final boolean isDynamicPricing;
    private final LocalDate validFrom;
    private final LocalDate validUntil;
    private final String sourceUrl;
    private final LocalDateTime lastVerifiedAt;

    public ChargingProviderTariff(UUID id, String empName, String tariffVariant, String cpoName, String priceTier,
                                  ChargingType chargingType, BigDecimal pricePerKwh, BigDecimal sessionFeeEur,
                                  BigDecimal monthlyFeeEur, BigDecimal blockingFeePerMin, Integer blockingFeeAfterMin,
                                  boolean isDynamicPricing, LocalDate validFrom, LocalDate validUntil,
                                  String sourceUrl, LocalDateTime lastVerifiedAt) {
        this.id = id;
        this.empName = empName;
        this.tariffVariant = tariffVariant;
        this.cpoName = cpoName;
        this.priceTier = priceTier;
        this.chargingType = chargingType;
        this.pricePerKwh = pricePerKwh;
        this.sessionFeeEur = sessionFeeEur;
        this.monthlyFeeEur = monthlyFeeEur;
        this.blockingFeePerMin = blockingFeePerMin;
        this.blockingFeeAfterMin = blockingFeeAfterMin;
        this.isDynamicPricing = isDynamicPricing;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.sourceUrl = sourceUrl;
        this.lastVerifiedAt = lastVerifiedAt;
    }

    public UUID getId() { return id; }
    public String getEmpName() { return empName; }
    public String getTariffVariant() { return tariffVariant; }
    public String getCpoName() { return cpoName; }
    public String getPriceTier() { return priceTier; }
    public ChargingType getChargingType() { return chargingType; }
    public BigDecimal getPricePerKwh() { return pricePerKwh; }
    public BigDecimal getSessionFeeEur() { return sessionFeeEur; }
    public BigDecimal getMonthlyFeeEur() { return monthlyFeeEur; }
    public BigDecimal getBlockingFeePerMin() { return blockingFeePerMin; }
    public Integer getBlockingFeeAfterMin() { return blockingFeeAfterMin; }
    public boolean isDynamicPricing() { return isDynamicPricing; }
    public LocalDate getValidFrom() { return validFrom; }
    public LocalDate getValidUntil() { return validUntil; }
    public String getSourceUrl() { return sourceUrl; }
    public LocalDateTime getLastVerifiedAt() { return lastVerifiedAt; }
}
