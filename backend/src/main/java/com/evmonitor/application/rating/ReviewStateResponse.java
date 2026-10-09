package com.evmonitor.application.rating;

import com.evmonitor.domain.rating.CarReview;
import com.evmonitor.domain.rating.ReviewStatus;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * Stand des eigenen Erfahrungstexts für den Fahrer: die jüngste nicht ersetzte Fassung,
 * ob eine Fassung öffentlich ist, und die Löscheinstellung. Ohne Moderationsdetails.
 */
public record ReviewStateResponse(
        String body,
        List<String> pros,
        List<String> cons,
        ReviewStatus status,
        String rejectionReason,
        String rejectionNote,
        boolean published,
        boolean deleteWithCar,
        LocalDateTime submittedAt) {

    /** @return null, wenn es keinen Text gibt */
    static ReviewStateResponse of(List<CarReview> versions) {
        CarReview latest = versions.stream()
                .filter(v -> v.getStatus() != ReviewStatus.SUPERSEDED)
                .max(Comparator.comparing(CarReview::getCreatedAt))
                .orElse(null);
        if (latest == null) return null;
        boolean published = versions.stream().anyMatch(v -> v.getStatus() == ReviewStatus.APPROVED);
        return new ReviewStateResponse(latest.getBody(), latest.getPros(), latest.getCons(), latest.getStatus(),
                latest.getRejectionReason(), latest.getRejectionNote(), published, latest.isDeleteWithCar(),
                latest.getCreatedAt());
    }
}
