package com.evmonitor.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Fahrverbrauch anderer Fahrer einer Gruppe von Autos (Variante oder Modell). Zählt nur Autos, die für
 * sich {@link DriveConsumption#isReliable()} erfüllen, und erst ab {@link #MIN_USERS} verschiedenen
 * anderen Nutzern, der Fragende fällt vorher heraus. Streckengewichtet (Summe kWh durch Summe km).
 *
 * <p>Datenschutz-Entscheidung 01.11.2026: zwei andere Fahrer reichen. Bei genau zwei sieht jeder den
 * Schnitt der beiden anderen, das ist akzeptiert, damit Modelle mit wenigen Fahrern nicht leer bleiben.
 *
 * @param consumptionKwhPer100km null unter der Mindestzahl
 * @param users                  verschiedene Nutzer im Schnitt
 */
public record ModelDriveAverage(BigDecimal consumptionKwhPer100km, int users) {

    public static final int MIN_USERS = 2;

    public static final ModelDriveAverage NONE = new ModelDriveAverage(null, 0);

    /** Fahrverbrauch eines Autos mit seinem Halter, Baustein des Modellschnitts. */
    public record CarDrive(UUID userId, DriveConsumption drive) {}

    /** Schnitt ohne die Autos von {@code excludedUserId} (der Fragende). */
    public static ModelDriveAverage of(List<CarDrive> cars, UUID excludedUserId) {
        DriveConsumption sum = DriveConsumption.EMPTY;
        Set<UUID> users = new HashSet<>();
        for (CarDrive c : cars) {
            if (c.userId() == null || c.userId().equals(excludedUserId) || !c.drive().isReliable()) continue;
            sum = sum.plus(c.drive());
            users.add(c.userId());
        }
        if (users.size() < MIN_USERS) return new ModelDriveAverage(null, users.size());
        BigDecimal value = sum.kwh().multiply(new BigDecimal("100"))
                .divide(sum.kmWithEnergy(), 2, RoundingMode.HALF_UP);
        return new ModelDriveAverage(value, users.size());
    }
}
