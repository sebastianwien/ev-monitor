package com.evmonitor.application.ingest;

import com.evmonitor.domain.ChargingType;
import com.evmonitor.domain.EnergyMeasurementType;
import com.evmonitor.domain.EnergySource;
import com.evmonitor.domain.RouteType;
import com.evmonitor.domain.TireType;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Eine Ladung, wie sie ein Adapter dem {@link IngestGateway} übergibt: bereits geparst (Datum,
 * Geohash, Enums), aber noch ohne Regeln angewandt. Alles außer {@code loggedAt} ist optional.
 *
 * @param loggedAt       Start der Ladung in UTC, noch nicht auf die Minute gekürzt
 * @param socStartMissed R15: Ladestart nicht beobachtet, {@code socBefore} wird hergeleitet
 */
@Builder
public record ChargingEntry(
        LocalDateTime loggedAt,
        BigDecimal kwhCharged,
        BigDecimal kwhAtVehicle,
        EnergyMeasurementType measurementType,
        EnergySource energySource,
        BigDecimal costEur,
        BigDecimal pricePerKwh,
        Integer chargeDurationMinutes,
        String geohash,
        Boolean publicCharging,
        String cpoName,
        Integer odometerKm,
        Integer odometerSuggestionMinKm,
        Integer odometerSuggestionMaxKm,
        BigDecimal maxChargingPowerKw,
        BigDecimal socBefore,
        BigDecimal socAfter,
        Boolean socStartMissed,
        ChargingType chargingType,
        RouteType routeType,
        TireType tireType,
        Double temperatureCelsius,
        String rawImportData,
        String powerCurvePointsJson,
        String socCurvePointsJson) {
}
