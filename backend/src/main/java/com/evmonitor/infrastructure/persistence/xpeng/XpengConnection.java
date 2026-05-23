package com.evmonitor.infrastructure.persistence.xpeng;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "xpeng_connection")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class XpengConnection {

    public static final String CURRENT_CONSENT_VERSION = "v1.0";
    public static final String AUTOSYNC_CONSENT_VERSION = "v2.0";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "car_id", nullable = false, unique = true)
    private UUID carId;

    @Column(name = "vin", nullable = false, length = 17)
    private String vin;

    @Column(name = "consent_granted_at", nullable = false)
    private LocalDateTime consentGrantedAt;

    @Column(name = "consent_revoked_at")
    private LocalDateTime consentRevokedAt;

    @Column(name = "consent_ip", length = 45)
    private String consentIp;

    @Column(name = "consent_user_agent", columnDefinition = "TEXT")
    private String consentUserAgent;

    @Column(name = "consent_version", nullable = false, length = 10)
    private String consentVersion;

    @Column(name = "routing_token", nullable = false, unique = true)
    private UUID routingToken;

    @Column(name = "auto_sync_enabled", nullable = false)
    private boolean autoSyncEnabled;

    @Column(name = "xpeng_email", length = 255)
    private String xpengEmail;

    @Column(name = "last_request_sent_at")
    private LocalDateTime lastRequestSentAt;

    @Column(name = "last_successful_import_at")
    private LocalDateTime lastSuccessfulImportAt;

    @Column(name = "total_imports_count", nullable = false)
    private int totalImportsCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (consentVersion == null) consentVersion = CURRENT_CONSENT_VERSION;
        if (routingToken == null) routingToken = UUID.randomUUID();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean isActive() {
        return consentRevokedAt == null;
    }
}
