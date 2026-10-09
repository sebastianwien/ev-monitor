package com.evmonitor.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "promo_redemption")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PromoRedemptionEntity {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false)
    private String code;

    @Column(name = "redeemed_at", nullable = false)
    private LocalDateTime redeemedAt;
}
