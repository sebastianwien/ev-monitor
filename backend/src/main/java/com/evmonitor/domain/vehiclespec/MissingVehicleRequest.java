package com.evmonitor.domain.vehiclespec;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Meldung "Fehlt etwas?": ein Nutzer vermisst eine Variante, ein Modell oder einen Hersteller.
 * Die Kontakt-Mail dient nur der Rückmeldung durch den Admin und wird 30 Tage nach Erledigung genullt,
 * bei nie erledigten Meldungen spätestens 90 Tage nach Eingang.
 */
@Entity
@Table(name = "missing_vehicle_request")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MissingVehicleRequest {

    @Id
    private UUID id;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "contact_email", length = 254)
    private String contactEmail;
    @Column(name = "free_text", nullable = false, length = 150)
    private String freeText;
    @Column(length = 60)
    private String brand;
    @Column(length = 80)
    private String model;
    @Column(name = "net_kwh", precision = 6, scale = 2)
    private BigDecimal netKwh;
    @Column(name = "build_year")
    private Integer buildYear;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "admin_notified_at")
    private LocalDateTime adminNotifiedAt;
    @Column(name = "done_at")
    private LocalDateTime doneAt;
    @Column(name = "user_notified_at")
    private LocalDateTime userNotifiedAt;
    @Column(name = "done_by_admin_id")
    private UUID doneByAdminId;

    /** Bereits bereinigte Felder (Text ohne Tags, Mail geprüft); die Prüfung macht der Service. */
    public record Draft(String contactEmail, String freeText, String brand, String model,
                        BigDecimal netKwh, Integer buildYear) {
    }

    public static MissingVehicleRequest create(UUID userId, Draft draft) {
        MissingVehicleRequest r = new MissingVehicleRequest();
        r.id = UUID.randomUUID();
        r.userId = userId;
        r.contactEmail = draft.contactEmail();
        r.freeText = draft.freeText();
        r.brand = draft.brand();
        r.model = draft.model();
        r.netKwh = draft.netKwh();
        r.buildYear = draft.buildYear();
        r.createdAt = LocalDateTime.now();
        return r;
    }

    public void markAdminNotified() {
        this.adminNotifiedAt = LocalDateTime.now();
    }

    public boolean isDone() {
        return doneAt != null;
    }

    public void markDone(UUID adminId) {
        this.doneAt = LocalDateTime.now();
        this.doneByAdminId = adminId;
    }

    public void markUserNotified() {
        this.userNotifiedAt = LocalDateTime.now();
    }

    public void purgeContactEmail() {
        this.contactEmail = null;
    }
}
