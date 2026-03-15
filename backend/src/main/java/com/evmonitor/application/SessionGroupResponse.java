package com.evmonitor.application;

import com.evmonitor.domain.ChargingSessionGroup;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Repräsentiert eine gruppierte Ladegruppe im Dashboard.
 * Wird neben normalen EvLogResponse-Einträgen im Dashboard-Feed angezeigt.
 */
public record SessionGroupResponse(
        UUID id,
        UUID carId,
        BigDecimal totalKwhCharged,
        BigDecimal costEur,
        Integer totalDurationMinutes,
        LocalDateTime sessionStart,
        LocalDateTime sessionEnd,
        int sessionCount,
        String geohash,
        String dataSource) {

    public static SessionGroupResponse fromDomain(ChargingSessionGroup group) {
        return new SessionGroupResponse(
                group.getId(),
                group.getCarId(),
                group.getTotalKwhCharged(),
                group.getCostEur(),
                group.getTotalDurationMinutes(),
                group.getSessionStart(),
                group.getSessionEnd(),
                group.getSessionCount(),
                group.getGeohash(),
                group.getDataSource());
    }
}
