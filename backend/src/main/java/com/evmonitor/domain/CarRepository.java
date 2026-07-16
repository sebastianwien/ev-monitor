package com.evmonitor.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.evmonitor.domain.CarBrand;

public interface CarRepository {
    Car save(Car car);

    Optional<Car> findById(UUID id);

    List<Car> findAllByUserId(UUID userId);

    List<Car> findAllByModel(CarBrand.CarModel model);

    long countByUserId(UUID userId);

    void deleteById(UUID id);

    /** Cars that have AT_VEHICLE ev_log entries but no SoH entry in the current calendar year. */
    List<Car> findCarsNeedingSohDetection();

    /** All cars linked to a given vehicle specification (across all users). */
    List<Car> findAllByVehicleSpecificationId(UUID vehicleSpecificationId);

    /**
     * Lightweight references to all cars that have a publicly shared photo
     * ({@code image_public = true} and a stored image), newest first.
     * Read model for the public model gallery - deliberately avoids loading full
     * {@link Car} aggregates (no spec lookup, no N+1).
     */
    List<CarPhotoRef> findPublicPhotoRefsNewestFirst();

    /** Ids of cars with a public photo for a single model, newest first. */
    List<UUID> findPublicPhotoCarIdsByModel(CarBrand.CarModel model);

    /**
     * Minimal projection: which car, which model (enum name as stored, read as a raw string so a
     * legacy/unmappable model value can never break the whole query). Carries no user-identifiable data.
     */
    record CarPhotoRef(UUID carId, String model) {}
}
