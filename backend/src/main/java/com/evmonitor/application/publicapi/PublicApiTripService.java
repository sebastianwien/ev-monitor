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
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class PublicApiTripService {

    private final EvTripRepository tripRepository;
    private final CarRepository carRepository;

    @Transactional
    public ApiTripResponse createTrip(UUID userId, PublicApiTripRequest request) {
        Car car = carRepository.findById(request.carId())
                .orElseThrow(() -> new IllegalArgumentException("Fahrzeug nicht gefunden"));
        if (!car.getUserId().equals(userId)) {
            throw new SecurityException("Dieses Fahrzeug gehört dir nicht");
        }

        OffsetDateTime start = parseTimestamp(request.startedAt(), "started_at");
        OffsetDateTime end = parseTimestamp(request.endedAt(), "ended_at");
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("ended_at muss nach started_at liegen");
        }

        EvTrip trip = EvTrip.builder()
                .carId(request.carId())
                .userId(userId)
                .dataSource(EvTrip.DATA_SOURCE_API_UPLOAD)
                .tripStartedAt(start)
                .tripEndedAt(end)
                .distanceKm(request.distanceKm())
                .socStart(request.socStart())
                .socEnd(request.socEnd())
                .routeType(request.routeType())
                .estimatedConsumedKwh(calculateEstimatedConsumedKwh(
                        request.socStart(), request.socEnd(), car))
                .status("COMPLETED")
                .userCreated(true)
                .build();

        return ApiTripResponse.fromDomain(tripRepository.save(trip));
    }

    @Transactional(readOnly = true)
    public ApiTripsPageResponse listTrips(UUID userId, UUID carId, String from, String to, int page, int size) {
        if (carId != null) {
            Car car = carRepository.findById(carId)
                    .orElseThrow(() -> new IllegalArgumentException("Fahrzeug nicht gefunden"));
            if (!car.getUserId().equals(userId)) {
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
