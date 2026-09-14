package com.evmonitor.infrastructure.persistence.xpeng;

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
 * Einwilligungs-Nachweis aus dem entfernten XPeng-Mail-Weg (Art. 7 DSGVO).
 * FK-lose Kopie von user_id (die Quelltabelle xpeng_connection existiert nicht mehr):
 * die Kontoloeschung purged Audit-Zeilen explizit ueber user_id, ein TTL-Job nach 3 Jahren.
 */
@Entity
@Table(name = "xpeng_consent_audit")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class XpengConsentAudit {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "vin", length = 17)
    private String vin;

    @Column(name = "consent_version", length = 16)
    private String consentVersion;

    @Column(name = "consent_granted_at")
    private LocalDateTime consentGrantedAt;

    @Column(name = "consent_revoked_at")
    private LocalDateTime consentRevokedAt;

    @Column(name = "consent_ip", length = 45)
    private String consentIp;

    @Column(name = "consent_user_agent")
    private String consentUserAgent;

    @Column(name = "archived_at", nullable = false)
    private LocalDateTime archivedAt;
}
