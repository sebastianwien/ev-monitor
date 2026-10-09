package com.evmonitor.application.rating;

import com.evmonitor.domain.UserRepository;
import com.evmonitor.domain.rating.CarRating;
import com.evmonitor.domain.rating.CarReview;
import com.evmonitor.infrastructure.email.EmailService;
import com.evmonitor.infrastructure.email.ReviewRejectedMail;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Mail an den Fahrer, wenn sein Text abgelehnt wurde. Mail-Fehler kippen die Entscheidung nicht. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewRejectionNotifier {

    private final UserRepository userRepository;
    private final EmailService emailService;

    public void notifyRejected(CarRating rating, CarReview review) {
        if (rating.getUserId() == null) return;
        userRepository.findById(rating.getUserId()).ifPresent(user -> {
            try {
                emailService.sendReviewRejectedEmail(new ReviewRejectedMail(user.getEmail(), user.getUsername(),
                        user.getRegistrationLocale(), rating.modelDisplayName(), review.getRejectionReason(),
                        review.getRejectionNote(), rating.getCarId()));
            } catch (RuntimeException e) {
                log.warn("[RATING] rejection mail failed review={}: {}", review.getId(), e.getClass().getSimpleName());
            }
        });
    }
}
