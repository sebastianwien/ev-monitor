package com.evmonitor.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Repräsentiert eine logische Ladegruppe aus mehreren Micro-Sessions (Überschussladen).
 * Mehrere WALLBOX_GOE-Sessions mit kurzem Abstand (< merge_gap_minutes) werden zu einer Gruppe zusammengefasst.
 */
@Getter
@AllArgsConstructor
public class ChargingSessionGroup {

    private final UUID id;
    private final UUID carId;
    private BigDecimal totalKwhCharged;
    private Integer totalDurationMinutes;
    private final LocalDateTime sessionStart;
    private LocalDateTime sessionEnd;
    private int sessionCount;
    private final String geohash;
    private BigDecimal costEur;
    private final String dataSource;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Erstellt eine neue Gruppe aus der ersten Sub-Session.
     */
    public static ChargingSessionGroup createFrom(EvLog firstSession) {
        LocalDateTime now = LocalDateTime.now();
        // sessionEnd = sessionStart + duration (falls vorhanden)
        LocalDateTime end = firstSession.getChargeDurationMinutes() != null
                ? firstSession.getLoggedAt().plusMinutes(firstSession.getChargeDurationMinutes())
                : firstSession.getLoggedAt();
        return new ChargingSessionGroup(
                UUID.randomUUID(),
                firstSession.getCarId(),
                firstSession.getKwhCharged() != null ? firstSession.getKwhCharged() : BigDecimal.ZERO,
                firstSession.getChargeDurationMinutes(),
                firstSession.getLoggedAt(),
                end,
                1,
                firstSession.getGeohash(),
                firstSession.getCostEur(),
                firstSession.getDataSource().name(),
                now,
                now);
    }

    /**
     * Erweitert die Gruppe um eine weitere Sub-Session.
     */
    public void addSubSession(EvLog subSession) {
        if (subSession.getKwhCharged() != null) {
            this.totalKwhCharged = this.totalKwhCharged.add(subSession.getKwhCharged());
        }
        if (subSession.getChargeDurationMinutes() != null) {
            this.totalDurationMinutes = (this.totalDurationMinutes != null ? this.totalDurationMinutes : 0)
                    + subSession.getChargeDurationMinutes();
        }
        // sessionEnd = Ende der neuen Sub-Session (start + duration, falls vorhanden)
        LocalDateTime subEnd = subSession.getChargeDurationMinutes() != null
                ? subSession.getLoggedAt().plusMinutes(subSession.getChargeDurationMinutes())
                : subSession.getLoggedAt();
        if (subEnd.isAfter(this.sessionEnd)) {
            this.sessionEnd = subEnd;
        }
        if (subSession.getCostEur() != null) {
            this.costEur = (this.costEur != null ? this.costEur : BigDecimal.ZERO).add(subSession.getCostEur());
        }
        this.sessionCount++;
        this.updatedAt = LocalDateTime.now();
    }
}
