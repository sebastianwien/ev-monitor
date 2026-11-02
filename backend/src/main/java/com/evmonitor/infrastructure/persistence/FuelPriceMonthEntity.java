package com.evmonitor.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Benzinpreis eines Monats (V225), Schlüssel ist der Monatserste. */
@Entity
@Table(name = "fuel_price_month")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class FuelPriceMonthEntity {

    @Id
    @Column(name = "month")
    private LocalDate month;

    @Column(name = "benzin_eur_per_l", nullable = false, precision = 5, scale = 3)
    private BigDecimal benzinEurPerL;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
