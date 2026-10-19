package com.evmonitor.infrastructure.persistence.seo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Tageswert je Seite aus der Search Console. Geschrieben per JDBC in {@link GscDailyRepository};
 * die Entity existiert für das Schema in den H2-Tests und als Dokumentation der Spalten.
 */
@Entity
@Table(name = "gsc_page_daily")
@IdClass(GscPageDailyEntity.Key.class)
@Getter
@NoArgsConstructor
public class GscPageDailyEntity {

    @Id
    @Column(name = "stat_date", nullable = false)
    private LocalDate day;
    @Id
    @Column(name = "path", nullable = false, length = 500)
    private String path;
    @Column(name = "clicks", nullable = false)
    private int clicks;
    @Column(name = "impressions", nullable = false)
    private int impressions;
    @Column(name = "avg_position", nullable = false, precision = 6, scale = 2)
    private BigDecimal avgPosition;

    public record Key(LocalDate day, String path) implements Serializable {
    }
}
