package com.evmonitor.application.rating;

import java.util.List;

/** GET /api/ratings/eligibility. Die Schwellen liefert der Server mit, damit das Frontend sie nicht dupliziert. */
public record RatingEligibilityResponse(int minLogs, int minKm, List<CarEligibility> cars) {
}
