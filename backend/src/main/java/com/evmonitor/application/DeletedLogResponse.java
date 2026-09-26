package com.evmonitor.application;

import com.evmonitor.domain.DataSource;
import com.evmonitor.domain.EvLog;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Gelöschter Ladevorgang für den Papierkorb: nur was zum Wiedererkennen nötig ist. */
public record DeletedLogResponse(UUID id, LocalDateTime loggedAt, BigDecimal kwhCharged, BigDecimal kwhAtVehicle,
                                 DataSource dataSource, LocalDateTime deletedAt) {

    public static DeletedLogResponse from(EvLog log) {
        return new DeletedLogResponse(log.getId(), log.getLoggedAt(), log.getKwhCharged(), log.getKwhAtVehicle(),
                log.getDataSource(), log.getDeletedAt());
    }
}
