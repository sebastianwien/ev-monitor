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

/** Tageswert je Seite, Land (ISO-3, klein) und Gerät (DESKTOP, MOBILE, TABLET). */
@Entity
@Table(name = "gsc_segment_daily")
@IdClass(GscSegmentDailyEntity.Key.class)
@Getter
@NoArgsConstructor
public class GscSegmentDailyEntity {

    @Id
    @Column(name = "stat_date", nullable = false)
    private LocalDate day;
    @Id
    @Column(name = "path", nullable = false, length = 500)
    private String path;
    @Id
    @Column(name = "country", nullable = false, length = 3)
    private String country;
    @Id
    @Column(name = "device", nullable = false, length = 10)
    private String device;
    @Column(name = "clicks", nullable = false)
    private int clicks;
    @Column(name = "impressions", nullable = false)
    private int impressions;

    public record Key(LocalDate day, String path, String country, String device) implements Serializable {
    }
}
