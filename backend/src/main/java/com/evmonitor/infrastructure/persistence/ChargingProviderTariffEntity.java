package com.evmonitor.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "charging_provider_tariffs")
@Getter
@Setter
@NoArgsConstructor
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
    private boolean dynamicPricing;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Column(name = "source_url")
    private String sourceUrl;

    @Column(name = "last_verified_at", nullable = false)
    private LocalDateTime lastVerifiedAt;
}
