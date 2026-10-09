package com.evmonitor.infrastructure.web;

import com.evmonitor.application.rating.CarRatingService;
import com.evmonitor.application.rating.MyRatingResponse;
import com.evmonitor.application.rating.RatingEligibilityResponse;
import com.evmonitor.application.rating.RatingRequest;
import com.evmonitor.application.rating.RatingResultResponse;
import com.evmonitor.application.rating.ReviewRequest;
import com.evmonitor.application.rating.ReviewSettingsRequest;
import com.evmonitor.application.rating.ReviewStateResponse;
import com.evmonitor.infrastructure.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Fahrerbewertung des eigenen Autos. Ownership prüft der Service in jeder Methode. */
@RestController
@RequiredArgsConstructor
public class CarRatingController {

    private final CarRatingService ratingService;

    @GetMapping("/api/ratings/eligibility")
    public RatingEligibilityResponse eligibility(@AuthenticationPrincipal UserPrincipal principal) {
        return ratingService.eligibility(principal.getUser().getId());
    }

    /** 204 ohne Bewertung - für das Formular ist das der Normalfall, kein Fehler. */
    @GetMapping("/api/cars/{carId}/rating")
    public ResponseEntity<MyRatingResponse> myRating(@PathVariable UUID carId,
                                                     @AuthenticationPrincipal UserPrincipal principal) {
        return ratingService.myRating(principal.getUser().getId(), carId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @PutMapping("/api/cars/{carId}/rating")
    public RatingResultResponse submit(@PathVariable UUID carId, @Valid @RequestBody RatingRequest request,
                                       @AuthenticationPrincipal UserPrincipal principal) {
        return ratingService.submit(principal.getUser().getId(), carId, request);
    }

    @PutMapping("/api/cars/{carId}/rating/review")
    public ReviewStateResponse submitReview(@PathVariable UUID carId, @Valid @RequestBody ReviewRequest request,
                                            @AuthenticationPrincipal UserPrincipal principal) {
        return ratingService.submitReview(principal.getUser().getId(), carId, request);
    }

    @PatchMapping("/api/cars/{carId}/rating/review/settings")
    public ReviewStateResponse updateReviewSettings(@PathVariable UUID carId,
                                                    @Valid @RequestBody ReviewSettingsRequest request,
                                                    @AuthenticationPrincipal UserPrincipal principal) {
        return ratingService.updateReviewSettings(principal.getUser().getId(), carId, request.deleteWithCar());
    }
}
