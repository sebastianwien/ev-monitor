package com.evmonitor.application.imports.vweuda;

import java.time.OffsetDateTime;

record VwEudaSession(
        OffsetDateTime startedAt,
        OffsetDateTime endedAt,
        int durationMin,
        Integer socBefore,
        Integer socAfter,
        /** Ungerundeter SoC-Zuwachs. Gesetzt, wenn kWh mangels Leistungssignal daraus errechnet werden muss. */
        Double socDeltaPct,
        String chargeType,
        Double maxChargingPowerKw,
        Double calculatedKwh,
        Integer odometerKm,
        Double temperatureCelsius
) {}
