package com.evmonitor.domain.rating;

import com.evmonitor.domain.CarBrand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface CarRatingRepository extends JpaRepository<CarRating, UUID> {

    Optional<CarRating> findByCarIdAndVersion(UUID carId, int version);

    List<CarRating> findAllByCarId(UUID carId);

    /** Auch Bewertungen, deren Auto schon endgültig gelöscht ist - für die Kontolöschung. */
    List<CarRating> findAllByUserId(UUID userId);

    @Query("SELECT r.carId FROM CarRating r WHERE r.carId IN :carIds")
    Set<UUID> findRatedCarIds(@Param("carIds") Collection<UUID> carIds);

    /** Grundmenge jeder Modellstatistik: ohne Seed, anonymisierte Bewertungen zählen mit. */
    List<CarRating> findAllByCarModel(CarBrand.CarModel carModel);

    List<CarRating> findAllByCarModelAndSeedFalse(CarBrand.CarModel carModel);
}
