package com.evmonitor.infrastructure.persistence.sample;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Pseudonymisierte Kopie einer hochgeladenen Herstellerdatei (VW EU-Data-Act, XPeng-CSV).
 * Dient als Testgrundlage fuer Decoder und Detektoren, gerade bei Dateien, die nicht erkannt wurden.
 * Pseudonymisiert, nicht anonym: deshalb user_id fuer die Kontoloeschung und TTL-Job nach 180 Tagen.
 */
@Entity
@Table(name = "import_sample")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportSample {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "provider", nullable = false, length = 20)
    private String provider;

    @Column(name = "channel", nullable = false, length = 20)
    private String channel;

    @Column(name = "car_model", length = 100)
    private String carModel;

    /** SHA-256 der Originaldatei, nur fuer die Dedup (dieselbe Datei aus Vorschau und Import). */
    @Column(name = "content_sha256", nullable = false, length = 64)
    private String contentSha256;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    /** Pseudonymisiertes ZIP. */
    @Column(name = "content", nullable = false)
    private byte[] content;

    @Column(name = "outcome", nullable = false, length = 20)
    private String outcome;

    @Column(name = "sessions_detected")
    private Integer sessionsDetected;

    @Column(name = "trips_detected")
    private Integer tripsDetected;

    @Column(name = "error", length = 500)
    private String error;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
