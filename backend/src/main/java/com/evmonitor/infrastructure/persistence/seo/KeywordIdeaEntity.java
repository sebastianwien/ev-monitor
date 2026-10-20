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
@Table(name = "keyword_idea")
@IdClass(KeywordIdeaEntity.Key.class)
@Getter
@NoArgsConstructor
public class KeywordIdeaEntity {

    @Id
    @Column(name = "month_start", nullable = false)
    private LocalDate monthStart;
    @Id
    @Column(name = "keyword", nullable = false, length = 500)
    private String keyword;
    @Column(name = "seed", nullable = false, length = 500)
    private String seed;
    @Column(name = "search_volume")
    private Integer searchVolume;
    @Column(name = "cpc", precision = 10, scale = 2)
    private BigDecimal cpc;

    public record Key(LocalDate monthStart, String keyword) implements Serializable {
    }
}
