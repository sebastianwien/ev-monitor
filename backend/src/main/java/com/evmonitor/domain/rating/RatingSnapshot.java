package com.evmonitor.domain.rating;

import com.evmonitor.domain.CarBrand;

import java.util.UUID;

/**
 * Stand von Auto und Nutzer beim Absenden. Wird eingefroren, damit die Bewertung
 * auch nach Auto- oder Kontolöschung im richtigen Modell und mit ihrem Gewicht zählt.
 */
public record RatingSnapshot(
        CarBrand.CarModel carModel,
        UUID vehicleSpecificationId,
        int kmDriven,
        int logCount,
        boolean businessCar,
        boolean seed) {
}
