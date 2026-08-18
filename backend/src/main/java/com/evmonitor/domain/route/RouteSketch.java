package com.evmonitor.domain.route;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Gecachte Verbindung fuer ein Geohash-Paar. Der Schluessel ist bewusst das Paar und nicht
 * die Fahrt: dieselbe Relation wiederholt sich bei Pendlern taeglich.
 */
@Entity
@Table(name = "route_sketch")
@IdClass(RouteSketch.Key.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RouteSketch {

    @Id
    @Column(name = "start_geohash", length = 12)
    private String startGeohash;

    @Id
    @Column(name = "end_geohash", length = 12)
    private String endGeohash;

    @Column(name = "polyline", nullable = false)
    private String polyline;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        private String startGeohash;
        private String endGeohash;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key other)) return false;
            return java.util.Objects.equals(startGeohash, other.startGeohash)
                    && java.util.Objects.equals(endGeohash, other.endGeohash);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(startGeohash, endGeohash);
        }
    }
}
