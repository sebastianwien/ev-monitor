package com.evmonitor.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tesla_connections")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeslaConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "access_token", columnDefinition = "TEXT", nullable = false)
    private String accessToken; // Will be encrypted

    @Column(name = "vehicle_id", nullable = false)
    private String vehicleId; // Tesla's vehicle_id

    @Column(name = "vehicle_name")
    private String vehicleName; // Display name like "SKY"

    @Column(name = "last_sync_at")
    private LocalDateTime lastSyncAt;

    @Column(name = "auto_import_enabled", nullable = false)
    @Builder.Default
    private boolean autoImportEnabled = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
