package com.evmonitor.infrastructure.persistence.ingest;

import com.evmonitor.application.ingest.event.ImportEventOutcome;
import com.evmonitor.domain.DataSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Eine Zeile Import-Protokoll: welche Quelle wann mit welchem Ergebnis wie viel importiert hat.
 * Keine Inhalte (keine Messwerte, Orte, Dateien); {@code error} ist über
 * {@link com.evmonitor.application.ingest.event.ImportEventErrors} bereinigt. user_id für die
 * Kontolöschung, TTL 90 Tage.
 */
@Entity
@Table(name = "import_event")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportEvent {

    /** Provider und Kanal, wenn eine Fahrt eine unbekannte Quelle meldet. */
    public static final String UNKNOWN = "UNKNOWN";

    @Id
    @Column(name = "id")
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "car_id")
    private UUID carId;

    @Column(name = "provider", nullable = false, length = 20)
    private String provider;

    @Column(name = "channel", nullable = false, length = 10)
    private String channel;

    @Column(name = "data_source", nullable = false, length = 40)
    private String dataSource;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 20)
    private ImportEventOutcome outcome;

    @Column(name = "sessions_imported", nullable = false)
    private int sessionsImported;

    @Column(name = "sessions_skipped", nullable = false)
    private int sessionsSkipped;

    @Column(name = "sessions_failed", nullable = false)
    private int sessionsFailed;

    @Column(name = "trips_imported", nullable = false)
    private int tripsImported;

    @Column(name = "trips_skipped", nullable = false)
    private int tripsSkipped;

    @Column(name = "error", length = 200)
    private String error;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** Vorbelegt mit Herkunft aus der Quelle. */
    public static ImportEventBuilder of(DataSource source, UUID userId, UUID carId) {
        return builder()
                .userId(userId)
                .carId(carId)
                .provider(source.provider().name())
                .channel(source.channel().name())
                .dataSource(source.name());
    }

    /** Für Fahrten, deren Quelle als Text kommt und sich nicht zuordnen lässt. */
    public static ImportEventBuilder ofUnknownSource(String rawSource, UUID userId, UUID carId) {
        String source = rawSource == null ? UNKNOWN : rawSource;
        return builder()
                .userId(userId)
                .carId(carId)
                .provider(UNKNOWN)
                .channel(UNKNOWN)
                .dataSource(source.length() > 40 ? source.substring(0, 40) : source);
    }
}
