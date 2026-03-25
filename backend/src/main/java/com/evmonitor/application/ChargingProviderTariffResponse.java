package com.evmonitor.application;

import com.evmonitor.domain.ChargingProviderTariff;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public class ChargingProviderTariffResponse {

    private UUID id;
    private String empName;
    private String tariffVariant;
    private String chargingType;
    private BigDecimal pricePerKwh;
    private BigDecimal sessionFeeEur;
    private BigDecimal monthlyFeeEur;
    private BigDecimal blockingFeePerMin;
    private Integer blockingFeeAfterMin;
    private boolean dynamicPricing;
    private LocalDate validFrom;
    private LocalDateTime lastVerifiedAt;

    public ChargingProviderTariffResponse() {}

    public ChargingProviderTariffResponse(ChargingProviderTariff tariff) {
        this.id = tariff.getId();
        this.empName = tariff.getEmpName();
        this.tariffVariant = tariff.getTariffVariant();
        this.chargingType = tariff.getChargingType().name();
        this.pricePerKwh = tariff.getPricePerKwh();
        this.sessionFeeEur = tariff.getSessionFeeEur();
        this.monthlyFeeEur = tariff.getMonthlyFeeEur();
        this.blockingFeePerMin = tariff.getBlockingFeePerMin();
        this.blockingFeeAfterMin = tariff.getBlockingFeeAfterMin();
        this.dynamicPricing = tariff.isDynamicPricing();
        this.validFrom = tariff.getValidFrom();
        this.lastVerifiedAt = tariff.getLastVerifiedAt();
    }

    public UUID getId() { return id; }
    public String getEmpName() { return empName; }
    public String getTariffVariant() { return tariffVariant; }
    public String getChargingType() { return chargingType; }
    public BigDecimal getPricePerKwh() { return pricePerKwh; }
    public BigDecimal getSessionFeeEur() { return sessionFeeEur; }
    public BigDecimal getMonthlyFeeEur() { return monthlyFeeEur; }
    public BigDecimal getBlockingFeePerMin() { return blockingFeePerMin; }
    public Integer getBlockingFeeAfterMin() { return blockingFeeAfterMin; }
    public boolean isDynamicPricing() { return dynamicPricing; }
    public LocalDate getValidFrom() { return validFrom; }
    public LocalDateTime getLastVerifiedAt() { return lastVerifiedAt; }
}
