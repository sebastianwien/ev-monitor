package com.evmonitor.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Strecke, um die der Kilometerstand zwischen zwei aufgezeichneten Fahrten gestiegen ist. */
public record OdometerGap(OffsetDateTime from, OffsetDateTime to, BigDecimal distanceKm) {
}
