package com.evmonitor.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "charging_site", uniqueConstraints = @UniqueConstraint(columnNames = {"geohash", "name_key"}))
@Getter
@Setter
@NoArgsConstructor
public class ChargingSiteEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "name_key", nullable = false, length = 100)
    private String nameKey;

    @Column(name = "cpo_name", length = 100)
    private String cpoName;

    @Column(name = "geohash", nullable = false, length = 7)
    private String geohash;

    @Column(name = "max_power_kw", precision = 6, scale = 1)
    private BigDecimal maxPowerKw;

    @Column(name = "charge_points", nullable = false)
    private int chargePoints;

    @Column(name = "fast_charging", nullable = false)
    private boolean fastCharging;

    @Column(name = "source", nullable = false, length = 20)
    private String source;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
