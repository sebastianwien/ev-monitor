package com.evmonitor.infrastructure.persistence;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "charging_provider_tariffs")
public class ChargingProviderTariffEntity {

    @Id
    private UUID id;

    @Column(name = "emp_name", nullable = false)
    private String empName;

    @Column(name = "tariff_variant")
    private String tariffVariant;

    @Column(name = "cpo_name")
    private String cpoName;

    @Column(name = "price_tier")
    private String priceTier;

    @Column(name = "charging_type", nullable = false)
    private String chargingType;

    @Column(name = "price_per_kwh", nullable = false, precision = 6, scale = 4)
    private BigDecimal pricePerKwh;

    @Column(name = "session_fee_eur", nullable = false, precision = 6, scale = 4)
    private BigDecimal sessionFeeEur;

    @Column(name = "monthly_fee_eur", nullable = false, precision = 8, scale = 2)
    private BigDecimal monthlyFeeEur;

    @Column(name = "blocking_fee_per_min", precision = 6, scale = 4)
    private BigDecimal blockingFeePerMin;

    @Column(name = "blocking_fee_after_min")
    private Integer blockingFeeAfterMin;

    @Column(name = "is_dynamic_pricing", nullable = false)
    private boolean isDynamicPricing;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Column(name = "source_url")
    private String sourceUrl;

    @Column(name = "last_verified_at", nullable = false)
    private LocalDateTime lastVerifiedAt;

    public ChargingProviderTariffEntity() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getEmpName() { return empName; }
    public void setEmpName(String empName) { this.empName = empName; }

    public String getTariffVariant() { return tariffVariant; }
    public void setTariffVariant(String tariffVariant) { this.tariffVariant = tariffVariant; }

    public String getCpoName() { return cpoName; }
    public void setCpoName(String cpoName) { this.cpoName = cpoName; }

    public String getPriceTier() { return priceTier; }
    public void setPriceTier(String priceTier) { this.priceTier = priceTier; }

    public String getChargingType() { return chargingType; }
    public void setChargingType(String chargingType) { this.chargingType = chargingType; }

    public BigDecimal getPricePerKwh() { return pricePerKwh; }
    public void setPricePerKwh(BigDecimal pricePerKwh) { this.pricePerKwh = pricePerKwh; }

    public BigDecimal getSessionFeeEur() { return sessionFeeEur; }
    public void setSessionFeeEur(BigDecimal sessionFeeEur) { this.sessionFeeEur = sessionFeeEur; }

    public BigDecimal getMonthlyFeeEur() { return monthlyFeeEur; }
    public void setMonthlyFeeEur(BigDecimal monthlyFeeEur) { this.monthlyFeeEur = monthlyFeeEur; }

    public BigDecimal getBlockingFeePerMin() { return blockingFeePerMin; }
    public void setBlockingFeePerMin(BigDecimal blockingFeePerMin) { this.blockingFeePerMin = blockingFeePerMin; }

    public Integer getBlockingFeeAfterMin() { return blockingFeeAfterMin; }
    public void setBlockingFeeAfterMin(Integer blockingFeeAfterMin) { this.blockingFeeAfterMin = blockingFeeAfterMin; }

    public boolean isDynamicPricing() { return isDynamicPricing; }
    public void setDynamicPricing(boolean dynamicPricing) { isDynamicPricing = dynamicPricing; }

    public LocalDate getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDate validFrom) { this.validFrom = validFrom; }

    public LocalDate getValidUntil() { return validUntil; }
    public void setValidUntil(LocalDate validUntil) { this.validUntil = validUntil; }

    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }

    public LocalDateTime getLastVerifiedAt() { return lastVerifiedAt; }
    public void setLastVerifiedAt(LocalDateTime lastVerifiedAt) { this.lastVerifiedAt = lastVerifiedAt; }
}
