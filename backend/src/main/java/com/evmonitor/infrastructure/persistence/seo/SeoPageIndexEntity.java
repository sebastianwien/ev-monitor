package com.evmonitor.infrastructure.persistence.seo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Schema-Entity für die H2-Tests und als Spaltendoku; geschrieben per JDBC in {@link SeoIndexRepository}. */
@Entity
@Table(name = "seo_page_index")
@Getter
@NoArgsConstructor
public class SeoPageIndexEntity {

    @Id
    @Column(name = "path", nullable = false, length = 500)
    private String path;
    @Column(name = "checked_at")
    private LocalDateTime checkedAt;
    @Column(name = "verdict", length = 30)
    private String verdict;
    @Column(name = "coverage_state", length = 300)
    private String coverageState;
    @Column(name = "last_crawl_at")
    private LocalDateTime lastCrawlAt;
    @Column(name = "google_canonical", length = 1000)
    private String googleCanonical;
    @Column(name = "indexnow_hash", length = 64)
    private String indexnowHash;
    @Column(name = "indexnow_at")
    private LocalDateTime indexnowAt;
}
