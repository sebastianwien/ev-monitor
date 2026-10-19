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

/** Schema-Entity für die H2-Tests und als Spaltendoku; geschrieben per JDBC in {@link SeoAuditRepository}. */
@Entity
@Table(name = "seo_page_vitals")
@IdClass(SeoPageVitalsEntity.Key.class)
@Getter
@NoArgsConstructor
public class SeoPageVitalsEntity {

    @Id
    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;
    @Id
    @Column(name = "path", nullable = false, length = 500)
    private String path;
    @Column(name = "lcp_ms")
    private Integer lcpMs;
    @Column(name = "cls", precision = 6, scale = 3)
    private BigDecimal cls;
    @Column(name = "inp_ms")
    private Integer inpMs;
    @Column(name = "source", nullable = false, length = 10)
    private String source;

    public record Key(LocalDate weekStart, String path) implements Serializable {
    }
}
