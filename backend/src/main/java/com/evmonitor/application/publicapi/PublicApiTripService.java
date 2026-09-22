package com.evmonitor.application.publicapi;

import com.evmonitor.domain.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class PublicApiTripService {

    private final EvTripRepository tripRepository;
    private final CarRepository carRepository;

    @Transactional
    public ApiTripResponse createTrip(UUID userId, PublicApiTripRequest request) {
        Car car = loadOwnedCar(userId, request.carId());

        EvTrip trip = buildTrip(userId, car, request);
        if (tripRepository.existsByCarIdAndTripStartedAtAndDeletedAtIsNull(car.getId(), trip.getTripStartedAt())) {
            throw new IllegalArgumentException("Für dieses Fahrzeug existiert bereits eine Fahrt mit diesem Startzeitpunkt");
        }

        return ApiTripResponse.fromDomain(tripRepository.save(trip));
    }

    /**
     * Bulk upload for the manual import. Ownership is checked once; per entry an
     * invalid row counts as error, an existing trip with the same start (any source,
     * or an earlier row of the same batch) counts as skipped. No coins are awarded.
     */
    @Transactional
    public ImportApiResult createTrips(UUID userId, UUID carId, List<PublicApiTripRequest> entries) {
        Car car = loadOwnedCar(userId, carId);

        int imported = 0, skipped = 0, errors = 0;
        Set<OffsetDateTime> seenStarts = new HashSet<>();
        for (PublicApiTripRequest entry : entries) {
            EvTrip trip;
            try {
                trip = buildTrip(userId, car, entry);
            } catch (IllegalArgumentException e) {
                errors++;
                continue;
            }
            OffsetDateTime startInstant = trip.getTripStartedAt().withOffsetSameInstant(ZoneOffset.UTC);
            if (!seenStarts.add(startInstant)
                    || tripRepository.existsByCarIdAndTripStartedAtAndDeletedAtIsNull(car.getId(), trip.getTripStartedAt())) {
                skipped++;
                continue;
            }
            tripRepository.save(trip);
            imported++;
        }
        log.info("Trip bulk import: user={} car={} rows={} imported={} skipped={} errors={}",
                userId, carId, entries.size(), imported, skipped, errors);
        return ImportApiResult.withoutIds(imported, skipped, errors);
    }

    private Car loadOwnedCar(UUID userId, UUID carId) {
        if (carId == null) throw new IllegalArgumentException("car_id darf nicht leer sein");
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new IllegalArgumentException("Fahrzeug nicht gefunden"));
        if (!car.isOwnedBy(userId)) {
            throw new SecurityException("Dieses Fahrzeug gehört dir nicht");
        }
        return car;
    }

    /** Validates and maps one request to an unsaved trip. Throws IllegalArgumentException on invalid data. */
    private EvTrip buildTrip(UUID userId, Car car, PublicApiTripRequest request) {
        OffsetDateTime start = parseTimestamp(request.startedAt(), "started_at");
        OffsetDateTime end = parseTimestamp(request.endedAt(), "ended_at");
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("ended_at muss nach started_at liegen");
        }
        requireRange(request.distanceKm(), 0, 100_000, "distance_km");
        requireRange(request.odometerStartKm(), 0, 10_000_000, "odometer_start_km");
        requireRange(request.odometerEndKm(), 0, 10_000_000, "odometer_end_km");
        BigDecimal distanceKm = resolveDistance(request);
        requireRange(request.socStart(), 0, 100, "soc_start");
        requireRange(request.socEnd(), 0, 100, "soc_end");
        if (request.routeType() != null && !ALLOWED_ROUTE_TYPES.contains(request.routeType())) {
            throw new IllegalArgumentException("route_type muss CITY, COMBINED oder HIGHWAY sein");
        }

        return EvTrip.builder()
                .carId(car.getId())
                .userId(userId)
                .dataSource(EvTrip.DATA_SOURCE_API_UPLOAD)
                .tripStartedAt(start)
                .tripEndedAt(end)
                .distanceKm(distanceKm)
                .odometerStartKm(request.odometerStartKm())
                .odometerEndKm(request.odometerEndKm())
                .socStart(request.socStart())
                .socEnd(request.socEnd())
                .routeType(request.routeType())
                .estimatedConsumedKwh(calculateEstimatedConsumedKwh(
                        request.socStart(), request.socEnd(), car))
                .status("COMPLETED")
                .userCreated(true)
                .build();
    }

    /**
     * A trip without a distance carries no information the app can use, so either
     * distance_km or both odometer readings are required; the latter derive the distance.
     */
    private static BigDecimal resolveDistance(PublicApiTripRequest request) {
        if (request.distanceKm() != null) {
            if (request.distanceKm().signum() <= 0) {
                throw new IllegalArgumentException("distance_km muss größer als 0 sein");
            }
            return request.distanceKm();
        }
        if (request.odometerStartKm() != null && request.odometerEndKm() != null) {
            BigDecimal delta = request.odometerEndKm().subtract(request.odometerStartKm());
            if (delta.signum() <= 0) {
                throw new IllegalArgumentException("odometer_end_km muss größer als odometer_start_km sein");
            }
            return delta;
        }
        throw new IllegalArgumentException("distance_km oder odometer_start_km und odometer_end_km sind erforderlich");
    }

    private static final Set<String> ALLOWED_ROUTE_TYPES = Set.of("CITY", "COMBINED", "HIGHWAY");

    private static void requireRange(BigDecimal value, long min, long max, String field) {
        if (value == null) return;
        if (value.compareTo(BigDecimal.valueOf(min)) < 0 || value.compareTo(BigDecimal.valueOf(max)) > 0) {
            throw new IllegalArgumentException(field + " muss zwischen " + min + " und " + max + " liegen");
        }
    }

    @Transactional(readOnly = true)
    public ApiTripsPageResponse listTrips(UUID userId, UUID carId, String from, String to, int page, int size) {
        if (carId != null) {
            Car car = carRepository.findById(carId)
                    .orElseThrow(() -> new IllegalArgumentException("Fahrzeug nicht gefunden"));
            if (!car.isOwnedBy(userId)) {
                throw new SecurityException("Dieses Fahrzeug gehört dir nicht");
            }
        }

        OffsetDateTime fromDt = parseDate(from, false);
        OffsetDateTime toDt = parseDate(to, true);

        Page<EvTrip> result = tripRepository.findByUserIdAndFilters(
                userId, carId, fromDt, toDt, PageRequest.of(page, size));

        return new ApiTripsPageResponse(
                result.getContent().stream().map(ApiTripResponse::fromDomain).toList(),
                result.getTotalElements(),
                page,
                size,
                result.hasNext()
        );
    }

    @Transactional(readOnly = true)
    public ApiTripResponse getTrip(UUID userId, UUID tripId) {
        EvTrip trip = tripRepository.findById(tripId)
                .filter(t -> t.getDeletedAt() == null)
                .orElseThrow(() -> new NoSuchElementException("Trip nicht gefunden"));
        if (!trip.getUserId().equals(userId)) {
            throw new SecurityException("Kein Zugriff auf diesen Trip");
        }
        return ApiTripResponse.fromDomain(trip);
    }

    @Transactional
    public ApiTripResponse patchTrip(UUID userId, UUID tripId, PatchPublicTripRequest patch) {
        EvTrip trip = tripRepository.findById(tripId)
                .filter(t -> t.getDeletedAt() == null)
                .orElseThrow(() -> new NoSuchElementException("Trip nicht gefunden"));
        if (!trip.getUserId().equals(userId)) {
            throw new SecurityException("Kein Zugriff auf diesen Trip");
        }
        OffsetDateTime newStart = patch.startedAt() != null
                ? parseTimestamp(patch.startedAt(), "started_at") : trip.getTripStartedAt();
        OffsetDateTime newEnd = patch.endedAt() != null
                ? parseTimestamp(patch.endedAt(), "ended_at") : trip.getTripEndedAt();
        if (!newStart.isBefore(newEnd)) {
            throw new IllegalArgumentException("ended_at muss nach started_at liegen");
        }

        if (patch.startedAt() != null) trip.setTripStartedAt(newStart);
        if (patch.endedAt() != null) trip.setTripEndedAt(newEnd);
        if (patch.distanceKm() != null) trip.setDistanceKm(patch.distanceKm());
        if (patch.socStart() != null) trip.setSocStart(patch.socStart());
        if (patch.socEnd() != null) trip.setSocEnd(patch.socEnd());
        if (patch.routeType() != null) trip.setRouteType(patch.routeType());
        trip.setUserEditedAt(OffsetDateTime.now());

        if (patch.socStart() != null || patch.socEnd() != null) {
            Car car = carRepository.findById(trip.getCarId()).orElse(null);
            if (car != null) {
                trip.setEstimatedConsumedKwh(calculateEstimatedConsumedKwh(
                        trip.getSocStart(), trip.getSocEnd(), car));
            }
        }

        return ApiTripResponse.fromDomain(tripRepository.save(trip));
    }

    @Transactional
    public void deleteTrip(UUID userId, UUID tripId) {
        EvTrip trip = tripRepository.findById(tripId)
                .filter(t -> t.getDeletedAt() == null)
                .orElseThrow(() -> new NoSuchElementException("Trip nicht gefunden"));
        if (!trip.getUserId().equals(userId)) {
            throw new SecurityException("Kein Zugriff auf diesen Trip");
        }
        trip.setDeletedAt(OffsetDateTime.now());
        tripRepository.save(trip);
    }

    private OffsetDateTime parseDate(String raw, boolean endOfDay) {
        if (raw == null) return null;
        try {
            LocalDate date = LocalDate.parse(raw);
            return endOfDay
                    ? date.atTime(23, 59, 59).atOffset(ZoneOffset.UTC)
                    : date.atStartOfDay().atOffset(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Ungültiges Datumsformat. Erwartet: yyyy-MM-dd");
        }
    }

    private OffsetDateTime parseTimestamp(String value, String fieldName) {
        if (value == null) throw new IllegalArgumentException(fieldName + " darf nicht leer sein");
        try {
            return OffsetDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Ungültiges Datumsformat für " + fieldName + ". Erwartet: ISO 8601 mit Timezone-Offset");
        }
    }

    private BigDecimal calculateEstimatedConsumedKwh(BigDecimal socStart, BigDecimal socEnd, Car car) {
        if (socStart == null || socEnd == null) return null;
        BigDecimal delta = socStart.subtract(socEnd);
        if (delta.compareTo(BigDecimal.ZERO) <= 0) return null;
        BigDecimal capacity = car.getEffectiveBatteryCapacityKwh();
        if (capacity == null) return null;
        return delta.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP)
                .multiply(capacity)
                .setScale(2, RoundingMode.HALF_UP);
    }
}
