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

/** Schema-Entity für die H2-Tests und als Spaltendoku; geschrieben per JDBC in {@link SeoAuditRepository}. */
@Entity
@Table(name = "seo_page_audit")
@IdClass(SeoPageAuditEntity.Key.class)
@Getter
@NoArgsConstructor
public class SeoPageAuditEntity {

    @Id
    @Column(name = "stat_date", nullable = false)
    private LocalDate day;
    @Id
    @Column(name = "path", nullable = false, length = 500)
    private String path;
    @Column(name = "http_status", nullable = false)
    private int httpStatus;
    @Column(name = "score", nullable = false)
    private int score;
    @Column(name = "error_count", nullable = false)
    private int errorCount;
    @Column(name = "warning_count", nullable = false)
    private int warningCount;
    @Column(name = "findings", nullable = false, columnDefinition = "TEXT")
    private String findings;

    public record Key(LocalDate day, String path) implements Serializable {
    }
}
