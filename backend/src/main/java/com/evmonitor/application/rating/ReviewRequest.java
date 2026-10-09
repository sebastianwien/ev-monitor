package com.evmonitor.application.rating;

import com.evmonitor.domain.rating.ReviewDraft;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * PUT /api/cars/{carId}/rating/review. {@code deleteWithCar} null lässt die bisherige Einstellung stehen.
 */
public record ReviewRequest(
        @NotBlank @Size(max = ReviewDraft.MAX_BODY_CHARS) String body,
        @Size(max = ReviewDraft.MAX_LIST_ITEMS) List<@Size(max = ReviewDraft.MAX_ITEM_CHARS) String> pros,
        @Size(max = ReviewDraft.MAX_LIST_ITEMS) List<@Size(max = ReviewDraft.MAX_ITEM_CHARS) String> cons,
        Boolean deleteWithCar) {

    ReviewDraft toDraft() {
        return ReviewDraft.of(body, pros, cons);
    }
}
