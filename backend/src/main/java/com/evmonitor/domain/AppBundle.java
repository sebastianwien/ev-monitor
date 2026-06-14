package com.evmonitor.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * Ein veroeffentlichtes Web-Bundle (gezipptes Vite-Build) fuer Capgo Live-Updates.
 *
 * Die CI legt bei jedem Deploy eine neue Zeile an (Semver-Version + sha256-Checksum
 * + Dateiname im Bundle-Verzeichnis). Das neueste Bundle wird ueber createdAt bestimmt.
 */
@Entity
@Table(
    name = "app_bundle",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_app_bundle_version",
        columnNames = {"version"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppBundle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Semver-Version, z.B. "1.0.42". Eindeutig. */
    @Column(name = "version", nullable = false, length = 32)
    private String version;

    /** sha256-Checksum des Zips, von der Capgo-CLI erzeugt. */
    @Column(name = "checksum", nullable = false, length = 128)
    private String checksum;

    /** Dateiname im Bundle-Verzeichnis (kein Pfad), z.B. "1.0.42.zip". */
    @Column(name = "filename", nullable = false, length = 255)
    private String filename;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
