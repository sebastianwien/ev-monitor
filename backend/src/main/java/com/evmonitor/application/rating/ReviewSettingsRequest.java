package com.evmonitor.application.rating;

import jakarta.validation.constraints.NotNull;

/** PATCH /api/cars/{carId}/rating/review/settings */
public record ReviewSettingsRequest(@NotNull Boolean deleteWithCar) {
}
