package com.evmonitor.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "tessie_raw_imports",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_tessie_raw_imports_dedup",
        columnNames = {"user_id", "vin", "type", "tessie_id"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TessieRawImport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "vin", nullable = false, length = 17)
    private String vin;

    @Column(name = "type", nullable = false, length = 10)
    private String type;

    @Column(name = "tessie_id", nullable = false)
    private Long tessieId;

    @Column(name = "recorded_at", nullable = false)
    private OffsetDateTime recordedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw", nullable = false)
    private String raw;

    @Column(name = "imported_at", nullable = false, updatable = false)
    private OffsetDateTime importedAt;

    @Column(name = "processed", nullable = false)
    private boolean processed;

    @PrePersist
    void prePersist() {
        if (importedAt == null) {
            importedAt = OffsetDateTime.now();
        }
    }
}
