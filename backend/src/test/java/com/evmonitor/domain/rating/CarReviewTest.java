package com.evmonitor.domain.rating;

import com.evmonitor.domain.exception.ConflictException;
import com.evmonitor.domain.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Freigabe nur mit Schwärzungen, Ablehnung mit Grund, beides nur für offene Fassungen. */
class CarReviewTest {

    private static final UUID ADMIN = UUID.randomUUID();

    private static CarReview pending(String body) {
        return CarReview.submit(UUID.randomUUID(), ReviewDraft.of(body, List.of(), List.of()), new ReviewProof(1000, 5, null), false);
    }

    @Test
    void redaction_allowsOnlyReplacingNonEmptyPartsWithMarker() {
        String body = "Händler Meier in Köln, Kennzeichen K-AB 123, war super.";
        assertThat(CarReview.isRedactionOf(body, body)).isTrue();
        assertThat(CarReview.isRedactionOf(body, "Händler [entfernt] in Köln, Kennzeichen [entfernt], war super.")).isTrue();
        assertThat(CarReview.isRedactionOf(body, "[entfernt]")).isTrue();

        assertThat(CarReview.isRedactionOf(body, "Händler Meier in Köln war super.")).isFalse();
        assertThat(CarReview.isRedactionOf(body, "Händler Meier in Köln, Kennzeichen K-AB 123, war richtig super.")).isFalse();
        assertThat(CarReview.isRedactionOf("abc", "ab[entfernt]c")).isFalse();
    }

    @Test
    void approve_setsPublishedBodyAndReviewer() {
        CarReview review = pending("Händler Meier war super.");

        review.approve("Händler [entfernt] war super.", ADMIN);

        assertThat(review.getStatus()).isEqualTo(ReviewStatus.APPROVED);
        assertThat(review.getPublishedBody()).isEqualTo("Händler [entfernt] war super.");
        assertThat(review.getReviewedBy()).isEqualTo(ADMIN);
        assertThat(review.getReviewedAt()).isNotNull();
    }

    @Test
    void approve_withoutChanges_publishesBody() {
        CarReview review = pending("Alles gut.");
        review.approve(null, ADMIN);
        assertThat(review.getPublishedBody()).isEqualTo("Alles gut.");
    }

    @Test
    void approve_withEditedText_isRejected() {
        CarReview review = pending("Alles gut.");
        assertThatThrownBy(() -> review.approve("Alles sehr gut.", ADMIN)).isInstanceOf(ValidationException.class);
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.PENDING);
    }

    @Test
    void reject_otherNeedsNote() {
        assertThatThrownBy(() -> pending("x").reject(RejectionReason.OTHER, "  ", ADMIN)).isInstanceOf(ValidationException.class);

        CarReview review = pending("x");
        review.reject(RejectionReason.ADVERTISING, null, ADMIN);
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.REJECTED);
        assertThat(review.getRejectionReason()).isEqualTo("ADVERTISING");
    }

    @Test
    void onlyPendingVersionsCanBeDecided() {
        CarReview review = pending("x");
        review.approve(null, ADMIN);
        assertThatThrownBy(() -> review.reject(RejectionReason.INSULT, null, ADMIN)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> review.approve(null, ADMIN)).isInstanceOf(ConflictException.class);
    }
}
