package com.evmonitor.application.imports.eudataact;

import java.time.OffsetDateTime;

record EUDataActSession(
        OffsetDateTime startedAt,
        OffsetDateTime endedAt,
        int durationMin,
        Integer socBefore,
        Integer socAfter,
        String chargeType,
        Double maxChargingPowerKw,
        Double calculatedKwh,
        Integer odometerKm,
        Double temperatureCelsius
) {}
