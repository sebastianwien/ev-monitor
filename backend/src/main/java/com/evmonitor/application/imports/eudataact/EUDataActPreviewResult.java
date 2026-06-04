package com.evmonitor.application.imports.eudataact;

import java.time.OffsetDateTime;
import java.util.List;

public record EUDataActPreviewResult(
        String vin,
        List<SessionPreview> sessions
) {
    public record SessionPreview(
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

    static EUDataActPreviewResult from(EUDataActParseResult parsed) {
        List<SessionPreview> previews = parsed.sessions().stream()
                .map(s -> new SessionPreview(
                        s.startedAt(), s.endedAt(), s.durationMin(),
                        s.socBefore(), s.socAfter(), s.chargeType(),
                        s.maxChargingPowerKw(), s.calculatedKwh(),
                        s.odometerKm(), s.temperatureCelsius()))
                .toList();
        return new EUDataActPreviewResult(parsed.vin(), previews);
    }
}
