package com.evmonitor.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Zeit und Kilometerstand einer Fahrt, ohne Route und Rohdaten - genug, um Lücken zu finden. */
public record TripOdometerSpan(
        OffsetDateTime startedAt,
        OffsetDateTime endedAt,
        BigDecimal odometerStartKm,
        BigDecimal odometerEndKm,
        String dataSource) {

    public boolean isLiveSource() {
        return dataSource != null && EvTrip.LIVE_TRIP_SOURCES.contains(dataSource);
    }

    public boolean showsOdometerGapBefore() {
        return EvTrip.showsOdometerGaps(dataSource);
    }
}
