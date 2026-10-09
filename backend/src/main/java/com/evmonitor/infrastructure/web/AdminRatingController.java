package com.evmonitor.infrastructure.web;

import com.evmonitor.application.rating.AdminRatingResponses;
import com.evmonitor.application.rating.AdminRatingService;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.rating.RejectionReason;
import com.evmonitor.domain.rating.ReviewStatus;
import com.evmonitor.infrastructure.security.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Admin-Tab "Bewertungen": Liste, Detail, Freigabe (optional mit Schwärzung) und Ablehnung. */
@RestController
@RequestMapping("/api/admin/ratings")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminRatingController {

    private final AdminRatingService ratingService;

    public record ApproveRequest(@Size(max = 5_000) String publishedBody) {
    }

    public record RejectRequest(@NotNull RejectionReason reason, @Size(max = 1_000) String note) {
    }

    @GetMapping
    public AdminRatingResponses.Page list(@RequestParam(required = false) ReviewStatus status,
                                          @RequestParam(required = false) AdminRatingResponses.TextType type,
                                          @RequestParam(required = false) CarBrand.CarModel model,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "25") int size) {
        return ratingService.list(new AdminRatingService.Filter(status, type, model, page, size));
    }

    @GetMapping("/open-count")
    public long openCount() {
        return ratingService.countOpenTexts();
    }

    @GetMapping("/{ratingId}")
    public AdminRatingResponses.Detail detail(@PathVariable UUID ratingId) {
        return ratingService.detail(ratingId);
    }

    @PostMapping("/reviews/{reviewId}/approve")
    public AdminRatingResponses.Detail approve(@PathVariable UUID reviewId, @Valid @RequestBody(required = false) ApproveRequest request,
                                               @AuthenticationPrincipal UserPrincipal admin) {
        return ratingService.approve(reviewId, request == null ? null : request.publishedBody(), admin.getUser().getId());
    }

    @PostMapping("/reviews/{reviewId}/reject")
    public AdminRatingResponses.Detail reject(@PathVariable UUID reviewId, @Valid @RequestBody RejectRequest request,
                                              @AuthenticationPrincipal UserPrincipal admin) {
        return ratingService.reject(reviewId, request.reason(), request.note(), admin.getUser().getId());
    }
}
