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
@Table(name = "plausible_source_goal_daily")
@IdClass(PlausibleSourceGoalDailyEntity.Key.class)
@Getter
@NoArgsConstructor
public class PlausibleSourceGoalDailyEntity {

    @Id
    @Column(name = "stat_date", nullable = false)
    private LocalDate day;
    @Id
    @Column(name = "source", nullable = false, length = 200)
    private String source;
    @Id
    @Column(name = "utm_medium", nullable = false, length = 200)
    private String utmMedium;
    @Id
    @Column(name = "utm_campaign", nullable = false, length = 200)
    private String utmCampaign;
    @Id
    @Column(name = "goal", nullable = false, length = 100)
    private String goal;
    @Column(name = "visitors", nullable = false)
    private int visitors;

    public record Key(LocalDate day, String source, String utmMedium, String utmCampaign, String goal) implements Serializable {
    }
}
