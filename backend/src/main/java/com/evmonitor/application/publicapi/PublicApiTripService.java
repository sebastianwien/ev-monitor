package com.evmonitor.application.publicapi;

import com.evmonitor.application.ingest.IngestGateway;
import com.evmonitor.application.ingest.TripEntry;
import com.evmonitor.application.ingest.TripUploadCommand;
import com.evmonitor.application.ingest.TripUploadResult;
import com.evmonitor.domain.*;
import com.evmonitor.domain.exception.ForbiddenException;
import com.evmonitor.domain.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class PublicApiTripService {

    private final EvTripRepository tripRepository;
    private final CarRepository carRepository;
    private final IngestGateway ingestGateway;

    /** Ohne eigene Transaktion: das Gateway committet, erst danach wird ein ungültiger oder doppelter Eintrag gemeldet. */
    public ApiTripResponse createTrip(UUID userId, PublicApiTripRequest request) {
        TripEntry entry = null;
        IllegalArgumentException invalid = null;
        try {
            entry = toEntry(request);
        } catch (IllegalArgumentException e) {
            invalid = e;
        }
        // Auch ein ungültiger Eintrag geht leer ans Gateway: der Besitz wird vor den Daten geprüft (403 vor 400).
        TripUploadResult result = ingest(userId, request.carId(), entry == null ? List.of() : List.of(entry));
        if (invalid != null) throw invalid;
        if (result.created().isEmpty()) {
            throw new IllegalArgumentException("Für dieses Fahrzeug existiert bereits eine Fahrt mit diesem Startzeitpunkt");
        }
        return ApiTripResponse.fromDomain(result.created().get(0));
    }

    /**
     * Bulk upload for the manual import. An invalid row counts as error; ownership and duplicate rules
     * (same start on this car, any source, deleted trips and earlier rows included) live in the
     * {@link IngestGateway}. No coins are awarded.
     */
    public ImportApiResult createTrips(UUID userId, UUID carId, List<PublicApiTripRequest> entries) {
        List<TripEntry> valid = new ArrayList<>();
        int errors = 0;
        for (PublicApiTripRequest entry : entries) {
            try {
                valid.add(toEntry(entry));
            } catch (IllegalArgumentException e) {
                errors++;
            }
        }
        TripUploadResult result = ingest(userId, carId, valid);
        log.info("Trip bulk import: user={} car={} rows={} imported={} skipped={} errors={}",
                userId, carId, entries.size(), result.imported(), result.skipped(), errors);
        return ImportApiResult.withoutIds(result.imported(), result.skipped(), errors);
    }

    /** Übersetzt die Gateway-Fehler in die bisherigen Antworten: unbekannt 400, fremd 403. */
    private TripUploadResult ingest(UUID userId, UUID carId, List<TripEntry> entries) {
        if (carId == null) throw new IllegalArgumentException("car_id darf nicht leer sein");
        try {
            return ingestGateway.ingestTrips(new TripUploadCommand(userId, carId, DataSource.API_UPLOAD, entries));
        } catch (NotFoundException e) {
            throw new IllegalArgumentException("Fahrzeug nicht gefunden");
        } catch (ForbiddenException e) {
            throw new SecurityException("Dieses Fahrzeug gehört dir nicht");
        }
    }

    /** Validates and maps one request to a gateway entry. Throws IllegalArgumentException on invalid data. */
    private TripEntry toEntry(PublicApiTripRequest request) {
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
        return new TripEntry(start, end, distanceKm, request.odometerStartKm(), request.odometerEndKm(),
                request.socStart(), request.socEnd(), request.routeType());
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
            carRepository.findById(trip.getCarId()).ifPresent(car -> trip.setEstimatedConsumedKwh(
                    EvTrip.estimateConsumedKwh(trip.getSocStart(), trip.getSocEnd(), () -> Optional.of(car))));
        }

        // ingest-bypass: PATCH einer bestehenden Fahrt
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
        // ingest-bypass: löscht eine bestehende Fahrt (Soft-Delete)
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
}
