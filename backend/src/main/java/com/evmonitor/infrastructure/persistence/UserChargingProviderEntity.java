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
@Table(name = "user_charging_providers")
@Getter
@Setter
@NoArgsConstructor
public class UserChargingProviderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "provider_name", nullable = false, length = 100)
    private String providerName;

    @Column(name = "ac_price_per_kwh", precision = 6, scale = 4)
    private BigDecimal acPricePerKwh;

    @Column(name = "dc_price_per_kwh", precision = 6, scale = 4)
    private BigDecimal dcPricePerKwh;

    @Column(name = "monthly_fee_eur", nullable = false, precision = 8, scale = 2)
    private BigDecimal monthlyFeeEur = BigDecimal.ZERO;

    @Column(name = "session_fee_eur", nullable = false, precision = 6, scale = 4)
    private BigDecimal sessionFeeEur = BigDecimal.ZERO;

    @Column(name = "active_from", nullable = false)
    private LocalDate activeFrom;

    @Column(name = "active_until")
    private LocalDate activeUntil;

    @Column(name = "label", length = 100)
    private String label;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
