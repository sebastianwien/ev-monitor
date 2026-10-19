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

/** Tageswert je Suchbegriff und Seite. Suchbegriffe sind fremder Text: nur als Text behandeln, nie als HTML. */
@Entity
@Table(name = "gsc_query_daily")
@IdClass(GscQueryDailyEntity.Key.class)
@Getter
@NoArgsConstructor
public class GscQueryDailyEntity {

    @Id
    @Column(name = "stat_date", nullable = false)
    private LocalDate day;
    @Id
    @Column(name = "query", nullable = false, length = 500)
    private String query;
    @Id
    @Column(name = "path", nullable = false, length = 500)
    private String path;
    @Column(name = "clicks", nullable = false)
    private int clicks;
    @Column(name = "impressions", nullable = false)
    private int impressions;
    @Column(name = "avg_position", nullable = false, precision = 6, scale = 2)
    private BigDecimal avgPosition;
    @Column(name = "is_brand", nullable = false)
    private boolean brand;

    public record Key(LocalDate day, String query, String path) implements Serializable {
    }
}
