package com.evmonitor.application.rating;

import com.evmonitor.domain.rating.CarRating;
import com.evmonitor.domain.rating.CarRatingRepository;
import com.evmonitor.domain.rating.CarReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Was von Bewertungen beim Löschen übrig bleibt. Eigene Komponente ohne weitere Abhängigkeiten,
 * damit {@code CarService} und die Kontolöschung sie nutzen können, ohne Zyklen zu bauen.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CarRatingRetention {

    private final CarRatingRepository ratingRepository;
    private final CarReviewRepository reviewRepository;

    /**
     * Vor dem endgültigen Löschen eines Autos: Texte mit "mit dem Auto löschen" fallen weg,
     * die Bewertung zählt anonym weiter.
     */
    @Transactional
    public void beforeCarPurge(UUID carId) {
        List<CarRating> ratings = ratingRepository.findAllByCarId(carId);
        for (CarRating rating : ratings) {
            reviewRepository.deleteMarkedForCarDeletion(rating.getId());
            rating.detachFromCar();
        }
        ratingRepository.saveAllAndFlush(ratings);
    }

    /**
     * Kontolöschung: Bewertungen bleiben ohne Personenbezug, alle Texte werden gelöscht -
     * ein Freitext ist nie sicher anonym.
     */
    @Transactional
    public void anonymizeForAccount(UUID userId) {
        List<CarRating> ratings = ratingRepository.findAllByUserId(userId);
        if (ratings.isEmpty()) return;
        int texts = reviewRepository.deleteAllByRatingIds(ratings.stream().map(CarRating::getId).toList());
        ratings.forEach(CarRating::anonymize);
        ratingRepository.saveAllAndFlush(ratings);
        log.info("[ANONYMIZE] userId={} ratings={} reviewTexts={}", userId, ratings.size(), texts);
    }
}
