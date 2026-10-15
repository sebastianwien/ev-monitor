package com.evmonitor.domain.vehiclespec;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Änderungsprotokoll des Admin-Tabs Fahrzeug-Specs. Eine Zeile je Aktion, bei UPDATE eine je Feld.
 * specId hat keinen FK, damit das Protokoll einer gelöschten Zeile erhalten bleibt.
 */
@Entity
@Table(name = "vehicle_specification_change")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VehicleSpecificationChange {

    public enum Action { CREATE, UPDATE, DELETE, REASSIGN_CAR }

    @Id
    private UUID id;
    @Column(name = "spec_id", nullable = false)
    private UUID specId;
    @Column(name = "admin_user_id", nullable = false)
    private UUID adminUserId;
    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Action action;
    @Column(length = 60)
    private String field;
    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;
    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;
    @Column(columnDefinition = "TEXT")
    private String warning;

    public record Entry(UUID specId, UUID adminUserId, Action action, String field, String oldValue, String newValue, String warning) {
    }

    public static VehicleSpecificationChange of(Entry entry) {
        VehicleSpecificationChange c = new VehicleSpecificationChange();
        c.id = UUID.randomUUID();
        c.specId = entry.specId();
        c.adminUserId = entry.adminUserId();
        c.changedAt = LocalDateTime.now();
        c.action = entry.action();
        c.field = entry.field();
        c.oldValue = entry.oldValue();
        c.newValue = entry.newValue();
        c.warning = entry.warning();
        return c;
    }
}
