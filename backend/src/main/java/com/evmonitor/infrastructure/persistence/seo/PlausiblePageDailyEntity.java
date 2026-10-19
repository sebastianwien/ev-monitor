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

/** Schema-Entity für die H2-Tests und als Spaltendoku; geschrieben per JDBC in {@link PlausibleDailyRepository}. */
@Entity
@Table(name = "plausible_page_daily")
@IdClass(PlausiblePageDailyEntity.Key.class)
@Getter
@NoArgsConstructor
public class PlausiblePageDailyEntity {

    @Id
    @Column(name = "stat_date", nullable = false)
    private LocalDate day;
    @Id
    @Column(name = "path", nullable = false, length = 500)
    private String path;
    @Column(name = "visitors", nullable = false)
    private int visitors;
    @Column(name = "pageviews", nullable = false)
    private int pageviews;
    @Column(name = "entries", nullable = false)
    private int entries;
    @Column(name = "bounce_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal bounceRate;
    @Column(name = "visit_duration", nullable = false)
    private int visitDuration;

    public record Key(LocalDate day, String path) implements Serializable {
    }
}
