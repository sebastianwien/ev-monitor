package com.evmonitor.application.rating;

import java.time.LocalDate;
import java.util.UUID;

/** Bewertbarkeit eines eigenen Autos samt Fortschritt ("noch 2 Ladevorgänge") und Anzeigenamen. */
public record CarEligibility(
        UUID carId,
        String brandName,
        String modelName,
        boolean eligible,
        boolean rated,
        int kmDriven,
        int logCount,
        LocalDate firstLogDate) {
}
