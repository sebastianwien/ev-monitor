package com.evmonitor.domain.rating;

import com.evmonitor.domain.exception.ValidationException;

import java.util.List;
import java.util.stream.Stream;

/**
 * Antworten auf die sieben Kernfragen plus Stärken und Schwächen.
 * {@code longDistance} und {@code service} sind null bei "Kann ich nicht beurteilen".
 */
public record RatingAnswers(
        RebuyAnswer rebuy,
        List<RebuyReason> rebuyReasons,
        int dailyFit,
        Integer longDistance,
        int software,
        int buildQuality,
        Integer service,
        TotalCostVerdict totalCost,
        List<RatingChip> strengths,
        List<RatingChip> weaknesses) {

    /**
     * Prüft die fachlichen Regeln und liefert eine Kopie ohne Dubletten und ohne null-Listen.
     *
     * @throws ValidationException bei Sternen außerhalb 1 bis 5, fehlenden Pflichtantworten,
     *         Warum-Chips ohne "Marke ja, Modell nein" oder mehr als drei Stärken bzw. Schwächen
     */
    public RatingAnswers normalized() {
        if (rebuy == null || totalCost == null) {
            throw new ValidationException("RATING_INCOMPLETE", "Wiederkauf und Gesamtkosten sind Pflicht.");
        }
        if (Stream.of(dailyFit, longDistance, software, buildQuality, service)
                .anyMatch(s -> s != null && (s < 1 || s > 5))) {
            throw new ValidationException("RATING_STARS_RANGE", "Sterne müssen zwischen 1 und 5 liegen.");
        }
        List<RebuyReason> reasons = distinct(rebuyReasons);
        if (!reasons.isEmpty() && rebuy != RebuyAnswer.BRAND_OTHER_MODEL) {
            throw new ValidationException("RATING_REBUY_REASONS", "Gründe nur bei \"Marke ja, Modell nein\".");
        }
        List<RatingChip> pros = distinct(strengths);
        List<RatingChip> cons = distinct(weaknesses);
        if (pros.size() > RatingChip.MAX_PER_LIST || cons.size() > RatingChip.MAX_PER_LIST) {
            throw new ValidationException("RATING_TOO_MANY_CHIPS",
                    "Höchstens " + RatingChip.MAX_PER_LIST + " Stärken und Schwächen.");
        }
        return new RatingAnswers(rebuy, reasons, dailyFit, longDistance, software, buildQuality, service,
                totalCost, pros, cons);
    }

    private static <T> List<T> distinct(List<T> values) {
        return values == null ? List.of() : values.stream().filter(java.util.Objects::nonNull).distinct().toList();
    }
}
