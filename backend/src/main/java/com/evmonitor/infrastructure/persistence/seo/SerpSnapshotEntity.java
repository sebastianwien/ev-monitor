package com.evmonitor.infrastructure.persistence.seo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;

/** Schema-Entity für die H2-Tests und als Spaltendoku; geschrieben per JDBC in {@link SeoCompetitionRepository}. */
@Entity
@Table(name = "serp_snapshot")
@IdClass(SerpSnapshotEntity.Key.class)
@Getter
@NoArgsConstructor
public class SerpSnapshotEntity {

    @Id
    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;
    @Id
    @Column(name = "query", nullable = false, length = 500)
    private String query;
    @Id
    @Column(name = "serp_rank", nullable = false)
    private int rank;
    @Column(name = "domain", nullable = false, length = 255)
    private String domain;
    @Column(name = "url", nullable = false, length = 2000)
    private String url;
    @Column(name = "title", length = 500)
    private String title;

    public record Key(LocalDate weekStart, String query, int rank) implements Serializable {
    }
}
