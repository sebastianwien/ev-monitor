package com.evmonitor.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "promo_code")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PromoCodeEntity {

    @Id
    private String code;

    @Column(name = "trial_days", nullable = false)
    private int trialDays;

    @Column(name = "max_redemptions", nullable = false)
    private int maxRedemptions;

    @Column(name = "required_utm_source")
    private String requiredUtmSource;

    @Column(name = "valid_until")
    private LocalDateTime validUntil;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
