package com.evmonitor.domain.xpeng;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Eine erkannte Ladesession aus dem XPeng-Telemetrie-Stream.
 *
 * <p>Zwei Energie-Werte werden parallel geliefert:
 * <ul>
 *   <li>{@code kwhCharged} - <b>brutto</b>, AC-Eingang am Onboard-Charger
 *       (integriert aus {@code ldcu_chrgpwr}). Entspricht dem Wallbox-Zaehler.
 *   <li>{@code kwhAtVehicle} - <b>netto</b>, DC-Energie die in den Pack fliesst
 *       (integriert aus {@code -(bms_battvolt x bms_battcurr)} - das Minus weil
 *       XPeng beim Laden negative Stroeme meldet). {@code null} wenn keine
 *       validen U/I-Samples in der Session vorhanden waren.
 * </ul>
 * Bei AC-Laden ist {@code kwhAtVehicle} typisch 90-95 % von {@code kwhCharged}
 * (OBC-Wirkungsgrad). Bei DC-Laden sind beide Werte nahezu identisch.
 */
public record DetectedChargingSession(
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        BigDecimal socStart,
        BigDecimal socEnd,
        BigDecimal kwhCharged,
        BigDecimal kwhAtVehicle,
        BigDecimal maxPowerKw,
        BigDecimal odometerKm,
        String chargingType,
        Map<String, Object> telemetryExtras
) {
    public static String classifyChargingType(BigDecimal maxPowerKw) {
        if (maxPowerKw == null) return "UNKNOWN";
        return maxPowerKw.compareTo(new BigDecimal("22")) > 0 ? "DC" : "AC";
    }
}
