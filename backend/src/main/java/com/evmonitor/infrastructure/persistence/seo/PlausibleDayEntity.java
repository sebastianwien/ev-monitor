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
@Table(name = "plausible_day")
@Getter
@NoArgsConstructor
public class PlausibleDayEntity {

    @Id
    @Column(name = "stat_date", nullable = false)
    private LocalDate day;
    @Column(name = "visitors", nullable = false)
    private int visitors;
    @Column(name = "visits", nullable = false)
    private int visits;
    @Column(name = "pageviews", nullable = false)
    private int pageviews;
    @Column(name = "bounce_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal bounceRate;
    @Column(name = "visit_duration", nullable = false)
    private int visitDuration;
}
