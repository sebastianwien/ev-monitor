package com.evmonitor.domain.rating;

import com.evmonitor.domain.CarBrand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface CarReviewRepository extends JpaRepository<CarReview, UUID> {

    List<CarReview> findAllByCarRatingIdOrderByCreatedAtAsc(UUID carRatingId);

    /** Bewertungen eines Modells, hinter denen ein freigegebener Text steht (Gewichtung). */
    @Query("""
            SELECT DISTINCT v.carRatingId FROM CarReview v, CarRating r
            WHERE v.carRatingId = r.id AND r.carModel = :model
              AND v.status = com.evmonitor.domain.rating.ReviewStatus.APPROVED
            """)
    Set<UUID> findRatingIdsWithApprovedText(@Param("model") CarBrand.CarModel model);

    /** Öffentliche Texte eines Modells: nur freigegeben, ohne Seed. */
    @Query("""
            SELECT v FROM CarReview v, CarRating r
            WHERE v.carRatingId = r.id AND r.carModel = :model AND r.seed = false
              AND v.status = com.evmonitor.domain.rating.ReviewStatus.APPROVED
            ORDER BY v.reviewedAt DESC
            """)
    List<CarReview> findApprovedByModel(@Param("model") CarBrand.CarModel model);

    @Modifying
    @Query("DELETE FROM CarReview v WHERE v.carRatingId = :ratingId AND v.deleteWithCar = true")
    int deleteMarkedForCarDeletion(@Param("ratingId") UUID ratingId);

    @Modifying
    @Query("DELETE FROM CarReview v WHERE v.carRatingId IN :ratingIds")
    int deleteAllByRatingIds(@Param("ratingIds") Collection<UUID> ratingIds);
}
