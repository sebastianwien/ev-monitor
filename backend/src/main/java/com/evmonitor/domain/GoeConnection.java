package com.evmonitor.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "goe_connections")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoeConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "car_id", nullable = false)
    private UUID carId;

    @Column(name = "serial", nullable = false, unique = true)
    private String serial;

    @Column(name = "api_key", columnDefinition = "TEXT", nullable = false)
    private String apiKey; // AES-encrypted

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "car_state", nullable = false)
    @Builder.Default
    private int carState = 1; // 1=Idle, 2=Charging, 3=WaitCar, 4=Complete, 5=Error

    @Column(name = "session_started_at")
    private LocalDateTime sessionStartedAt;

    @Column(name = "last_polled_at")
    private LocalDateTime lastPolledAt;

    @Column(name = "last_poll_error")
    private String lastPollError;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

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
