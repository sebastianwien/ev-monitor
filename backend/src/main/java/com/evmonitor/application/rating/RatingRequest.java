package com.evmonitor.application.rating;

import com.evmonitor.domain.rating.RatingAnswers;
import com.evmonitor.domain.rating.RatingChip;
import com.evmonitor.domain.rating.RebuyAnswer;
import com.evmonitor.domain.rating.RebuyReason;
import com.evmonitor.domain.rating.TotalCostVerdict;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** PUT /api/cars/{carId}/rating. Unbekannte Enum-Werte lehnt schon Jackson ab. */
public record RatingRequest(
        @NotNull RebuyAnswer rebuy,
        @Size(max = 5) List<RebuyReason> rebuyReasons,
        @NotNull @Min(1) @Max(5) Integer dailyFit,
        @Min(1) @Max(5) Integer longDistance,
        @NotNull @Min(1) @Max(5) Integer software,
        @NotNull @Min(1) @Max(5) Integer buildQuality,
        @Min(1) @Max(5) Integer service,
        @NotNull TotalCostVerdict totalCost,
        @Size(max = RatingChip.MAX_PER_LIST) List<RatingChip> strengths,
        @Size(max = RatingChip.MAX_PER_LIST) List<RatingChip> weaknesses) {

    RatingAnswers toAnswers() {
        if (dailyFit == null || software == null || buildQuality == null) {
            throw new com.evmonitor.domain.exception.ValidationException("RATING_INCOMPLETE",
                    "Alltag, Software und Verarbeitung sind Pflicht.");
        }
        return new RatingAnswers(rebuy, rebuyReasons, dailyFit, longDistance, software, buildQuality, service,
                totalCost, strengths, weaknesses).normalized();
    }
}
