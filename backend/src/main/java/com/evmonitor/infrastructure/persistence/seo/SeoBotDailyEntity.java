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

/** Schema-Entity für die H2-Tests und als Spaltendoku; geschrieben per JDBC in {@link SeoBotRepository}. */
@Entity
@Table(name = "seo_bot_daily")
@IdClass(SeoBotDailyEntity.Key.class)
@Getter
@NoArgsConstructor
public class SeoBotDailyEntity {

    @Id
    @Column(name = "stat_date", nullable = false)
    private LocalDate day;
    @Id
    @Column(name = "bot", nullable = false, length = 30)
    private String bot;
    @Id
    @Column(name = "path", nullable = false, length = 500)
    private String path;
    @Column(name = "hits", nullable = false)
    private int hits;

    public record Key(LocalDate day, String bot, String path) implements Serializable {
    }
}
