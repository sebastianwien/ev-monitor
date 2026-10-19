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

/** Schema-Entity für die H2-Tests und als Spaltendoku; geschrieben per JDBC in {@link SeoCompetitionRepository}. */
@Entity
@Table(name = "keyword_metrics")
@IdClass(KeywordMetricsEntity.Key.class)
@Getter
@NoArgsConstructor
public class KeywordMetricsEntity {

    @Id
    @Column(name = "month_start", nullable = false)
    private LocalDate monthStart;
    @Id
    @Column(name = "query", nullable = false, length = 500)
    private String query;
    @Column(name = "search_volume")
    private Integer searchVolume;
    @Column(name = "cpc", precision = 10, scale = 2)
    private BigDecimal cpc;
    @Column(name = "competition", length = 10)
    private String competition;

    public record Key(LocalDate monthStart, String query) implements Serializable {
    }
}
