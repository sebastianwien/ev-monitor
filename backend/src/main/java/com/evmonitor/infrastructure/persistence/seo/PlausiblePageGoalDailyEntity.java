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
@Table(name = "plausible_page_goal_daily")
@IdClass(PlausiblePageGoalDailyEntity.Key.class)
@Getter
@NoArgsConstructor
public class PlausiblePageGoalDailyEntity {

    @Id
    @Column(name = "stat_date", nullable = false)
    private LocalDate day;
    @Id
    @Column(name = "path", nullable = false, length = 500)
    private String path;
    @Id
    @Column(name = "goal", nullable = false, length = 100)
    private String goal;
    @Column(name = "visitors", nullable = false)
    private int visitors;

    public record Key(LocalDate day, String path, String goal) implements Serializable {
    }
}
