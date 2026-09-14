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

    @Column(name = "max_ac_kw", precision = 6, scale = 1)
    private BigDecimal maxAcKw;

    @Column(name = "max_dc_kw", precision = 6, scale = 1)
    private BigDecimal maxDcKw;

    @Column(name = "charge_points", nullable = false)
    private int chargePoints;

    @Column(name = "register_id")
    private Integer registerId;

    @Column(name = "street", length = 150)
    private String street;

    @Column(name = "house_number", length = 20)
    private String houseNumber;

    @Column(name = "postal_code", length = 10)
    private String postalCode;

    @Column(name = "city", length = 100)
    private String city;

    /** Kommagetrennt, z. B. "Typ 2, CCS, CHAdeMO". */
    @Column(name = "plug_types", length = 100)
    private String plugTypes;

    @Column(name = "commissioned_on")
    private LocalDate commissionedOn;

    @Column(name = "site_label", length = 150)
    private String siteLabel;

    @Column(name = "payment", length = 200)
    private String payment;

    @Column(name = "opening_hours", length = 200)
    private String openingHours;

    @Column(name = "source", nullable = false, length = 20)
    private String source;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
