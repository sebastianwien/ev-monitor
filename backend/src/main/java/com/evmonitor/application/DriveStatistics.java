package com.evmonitor.application;

import java.math.BigDecimal;
import java.util.List;

/**
 * Fahrverbrauch für die Statistik eines Zeitraums, neben dem Verbrauch aus Ladungen. Die Reichweite
 * steht getrennt davon in {@code range} ({@code RangeEstimateService}).
 *
 * @param driveConsumptionKwhPer100km       eigene Fahrten ab 20 km im Zeitraum; null, wenn sie nicht reichen
 * @param driveSummerConsumptionKwhPer100km Mai bis August, alle Jahre
 * @param driveWinterConsumptionKwhPer100km November bis Februar, alle Jahre
 * @param modelDriveConsumptionKwhPer100km  andere Fahrer desselben Modells im Zeitraum, ab 2 anderen Nutzern; null ohne Zeitraum
 * @param modelDriveUsers                   Nutzer in diesem Schnitt
 * @param driveMonths                       Fahrverbrauch je Kalendermonat (UTC) im Zeitraum, nur Monate mit Wert;
 *                                          leer ohne Zeitraum
 */
public record DriveStatistics(
        BigDecimal driveConsumptionKwhPer100km,
        BigDecimal driveSummerConsumptionKwhPer100km,
        BigDecimal driveWinterConsumptionKwhPer100km,
        BigDecimal modelDriveConsumptionKwhPer100km,
        int modelDriveUsers,
        List<MonthDrive> driveMonths
) {
    /** @param month "2026-10" */
    public record MonthDrive(String month, BigDecimal consumptionKwhPer100km) {}
}
