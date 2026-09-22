package com.evmonitor.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.evmonitor.domain.CarBrand;

public interface CarRepository {
    Car save(Car car);

    /** Liefert nur nicht-gelöschte Fahrzeuge. */
    Optional<Car> findById(UUID id);

    /** Auch soft-gelöschte Fahrzeuge - ausschließlich für Restore und Purge. */
    Optional<Car> findByIdIncludingDeleted(UUID id);

    List<Car> findAllByUserId(UUID userId);

    List<Car> findAllByModel(CarBrand.CarModel model);

    long countByUserId(UUID userId);

    /** Hard-Delete inklusive FK-Kaskade. Nur der Purge-Job ruft das. */
    void deleteById(UUID id);

    /** Soft-gelöschte Fahrzeuge, deren Restore-Fenster abgelaufen ist. */
    List<Car> findSoftDeletedBefore(LocalDateTime cutoff);

    /**
     * Alle Fahrzeuge eines Users inklusive soft-gelöschter. Nur für DSGVO-Pfade
     * (Kontolöschung, Datenauskunft) - sonst gilt {@link #findAllByUserId}.
     */
    List<Car> findAllByUserIdIncludingDeleted(UUID userId);

    /** Cars that have AT_VEHICLE ev_log entries but no SoH entry in the current calendar year. */
    List<Car> findCarsNeedingSohDetection();

    /** All cars linked to a given vehicle specification (across all users). */
    List<Car> findAllByVehicleSpecificationId(UUID vehicleSpecificationId);

    // ── Oeffentliche Fahrzeugseite ───────────────────────────────────────────

    /** Aktueller Share-Token, leer wenn nicht geteilt. */
    Optional<String> findShareToken(UUID carId);

    void setShareToken(UUID carId, String token, LocalDateTime createdAt);

    void clearShareToken(UUID carId);

    /** Oeffentlicher Lookup - nur nicht-geloeschte Fahrzeuge. */
    Optional<Car> findByShareToken(String token);
}
