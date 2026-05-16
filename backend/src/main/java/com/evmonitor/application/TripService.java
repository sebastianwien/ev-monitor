package com.evmonitor.application;

import com.evmonitor.domain.*;
import com.evmonitor.domain.exception.ValidationException;
import com.evmonitor.domain.weather.TemperatureEnricher;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TripService {

    private final EvTripRepository tripRepository;
    private final CarRepository carRepository;
    private final ObjectMapper objectMapper;
    private final TemperatureEnricher temperatureEnricher;

    @Transactional
    public UUID saveTrip(InternalTripRequest req) {
        if (req.userId() == null) {
            throw new ValidationException("userId is required");
        }
        if (req.externalId() != null) {
            var existing = tripRepository.findByExternalIdAndDeletedAtIsNull(req.externalId());
            if (existing.isPresent()) {
                log.debug("Trip with externalId={} already exists - skipping", req.externalId());
                return existing.get().getId();
            }
        }

        EvTrip trip = EvTrip.builder()
                .externalId(req.externalId())
                .carId(req.carId())
                .userId(req.userId())
                .dataSource(req.dataSource())
                .tripStartedAt(req.tripStartedAt())
                .tripEndedAt(req.tripEndedAt())
                .socStart(req.socStart())
                .socEnd(req.socEnd())
                .odometerStartKm(req.odometerStartKm())
                .odometerEndKm(req.odometerEndKm())
                .distanceKm(req.distanceKm())
                .locationStartGeohash(req.locationStartGeohash())
                .locationEndGeohash(req.locationEndGeohash())
                .outsideTempCelsius(req.outsideTempCelsius())
                .energyRemainingStartKwh(req.energyRemainingStartKwh())
                .energyRemainingEndKwh(req.energyRemainingEndKwh())
                .estimatedConsumedKwh(req.estimatedConsumedKwh())
                .avgSpeedKmh(req.avgSpeedKmh())
                .maxSpeedKmh(req.maxSpeedKmh())
                .status(req.status() != null ? req.status() : "COMPLETED")
                .rawPayload(req.rawPayload())
                .telemetryExtras(req.telemetryExtras())
                .userCreated(false)
                .build();

        if (trip.getEstimatedConsumedKwh() == null) {
            trip.setEstimatedConsumedKwh(
                    calculateEstimatedConsumedKwh(req.socStart(), req.socEnd(), req.carId()));
        }

        EvTrip saved = tripRepository.save(trip);
        log.info("Trip saved: id={} externalId={} car={} distance={} km",
                saved.getId(), req.externalId(), req.carId(), req.distanceKm());

        if (req.outsideTempCelsius() == null
                && req.tripStartedAt() != null
                && (req.locationStartGeohash() != null || req.locationEndGeohash() != null)) {
            final UUID tripId = saved.getId();
            final String startGeohash = req.locationStartGeohash();
            final String endGeohash = req.locationEndGeohash();
            final LocalDateTime startedAt = req.tripStartedAt().toLocalDateTime();
            final LocalDateTime endedAt = req.tripEndedAt() != null ? req.tripEndedAt().toLocalDateTime() : null;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    temperatureEnricher.enrichTrip(tripId, startGeohash, endGeohash, startedAt, endedAt);
                }
            });
        }

        return saved.getId();
    }

    /**
     * Creates a manual trip for the given user. Seit Mai 2026 ohne Subscription-Gate -
     * manuelles Trip-Anlegen ist fuer alle authentifizierten User frei.
     */
    @Transactional
    public EvTripResponse createUserTrip(User user, CreateTripRequest req) {
        if (!req.tripStartedAt().isBefore(req.tripEndedAt())) {
            throw new IllegalArgumentException("tripStartedAt must be before tripEndedAt");
        }
        Car car = carRepository.findById(req.carId())
                .orElseThrow(() -> new IllegalArgumentException("Car not found"));
        if (!car.getUserId().equals(user.getId())) {
            throw new IllegalArgumentException("Car not owned by user");
        }

        EvTrip trip = EvTrip.builder()
                .carId(req.carId())
                .userId(user.getId())
                .dataSource("USER_CREATED")
                .tripStartedAt(req.tripStartedAt())
                .tripEndedAt(req.tripEndedAt())
                .distanceKm(req.distanceKm())
                .socStart(req.socStart())
                .socEnd(req.socEnd())
                .routeType(req.routeType())
                .status("COMPLETED")
                .userCreated(true)
                .build();

        return EvTripResponse.fromDomain(tripRepository.save(trip));
    }

    @Transactional
    public EvTripResponse updateTrip(UUID tripId, User user, UpdateTripRequest req) {
        EvTrip trip = tripRepository.findById(tripId)
                .filter(t -> t.getUserId().equals(user.getId()))
                .filter(t -> t.getDeletedAt() == null)
                .filter(t -> userCanSeeTrip(t, user))
                .orElseThrow(() -> new IllegalArgumentException("Trip not found"));

        OffsetDateTime newStart = req.tripStartedAt() != null ? req.tripStartedAt() : trip.getTripStartedAt();
        OffsetDateTime newEnd   = req.tripEndedAt()   != null ? req.tripEndedAt()   : trip.getTripEndedAt();
        if (!newStart.isBefore(newEnd)) {
            throw new ValidationException("Endzeit muss nach der Startzeit liegen");
        }

        boolean dataChanged = false;
        boolean anyChanged  = false;
        if (req.tripStartedAt() != null) { trip.setTripStartedAt(req.tripStartedAt()); dataChanged = true; anyChanged = true; }
        if (req.tripEndedAt() != null)   { trip.setTripEndedAt(req.tripEndedAt());     dataChanged = true; anyChanged = true; }
        if (req.distanceKm() != null)    { trip.setDistanceKm(req.distanceKm());       dataChanged = true; anyChanged = true; }
        if (req.routeType() != null)     { trip.setRouteType(req.routeType());         dataChanged = true; anyChanged = true; }
        if (req.socStart() != null)      { trip.setSocStart(req.socStart()); dataChanged = true; anyChanged = true; }
        if (req.socEnd() != null)        { trip.setSocEnd(req.socEnd());     dataChanged = true; anyChanged = true; }
        if (req.feedback() != null)      { trip.setFeedback(req.feedback());           anyChanged = true; }
        if (!anyChanged) return EvTripResponse.fromDomain(trip);

        if (dataChanged) trip.setUserEditedAt(OffsetDateTime.now());
        return EvTripResponse.fromDomain(tripRepository.save(trip));
    }

    @Transactional
    public void deleteTrip(UUID tripId, User user) {
        EvTrip trip = tripRepository.findById(tripId)
                .filter(t -> t.getUserId().equals(user.getId()))
                .filter(t -> t.getDeletedAt() == null)
                .filter(t -> userCanSeeTrip(t, user))
                .orElseThrow(() -> new IllegalArgumentException("Trip not found"));
        trip.setDeletedAt(OffsetDateTime.now());
        tripRepository.save(trip);
    }

    @Transactional
    public EvTripResponse mergeTrips(UUID survivingTripId, UUID mergeWithTripId, User user) {
        if (survivingTripId.equals(mergeWithTripId)) {
            throw new ValidationException("Eine Fahrt kann nicht mit sich selbst zusammengeführt werden");
        }
        EvTrip surviving = tripRepository.findById(survivingTripId)
                .filter(t -> t.getUserId().equals(user.getId()))
                .filter(t -> t.getDeletedAt() == null)
                .filter(t -> userCanSeeTrip(t, user))
                .orElseThrow(() -> new IllegalArgumentException("Trip not found"));

        EvTrip other = tripRepository.findById(mergeWithTripId)
                .filter(t -> t.getUserId().equals(user.getId()))
                .filter(t -> t.getDeletedAt() == null)
                .filter(t -> userCanSeeTrip(t, user))
                .orElseThrow(() -> new IllegalArgumentException("Merge-with trip not found"));

        if (!surviving.getCarId().equals(other.getCarId())) {
            throw new ValidationException("Trips müssen zum selben Auto gehören");
        }

        boolean survivingIsEarlier = surviving.getTripStartedAt().isBefore(other.getTripStartedAt());
        EvTrip earlier = survivingIsEarlier ? surviving : other;
        EvTrip later   = survivingIsEarlier ? other   : surviving;

        BigDecimal mergedSocStart = earlier.getSocStart();
        BigDecimal mergedSocEnd   = later.getSocEnd();
        BigDecimal estimatedConsumedKwh = calculateEstimatedConsumedKwh(
                mergedSocStart, mergedSocEnd, surviving.getCarId());
        if (estimatedConsumedKwh == null) {
            estimatedConsumedKwh = sumNullable(
                    earlier.getEstimatedConsumedKwh(), later.getEstimatedConsumedKwh(), 2);
        }
        surviving.setTripStartedAt(earlier.getTripStartedAt());
        surviving.setTripEndedAt(later.getTripEndedAt());
        surviving.setSocStart(mergedSocStart);
        surviving.setSocEnd(mergedSocEnd);
        surviving.setOdometerStartKm(earlier.getOdometerStartKm());
        surviving.setOdometerEndKm(later.getOdometerEndKm());
        surviving.setEnergyRemainingStartKwh(earlier.getEnergyRemainingStartKwh());
        surviving.setEnergyRemainingEndKwh(later.getEnergyRemainingEndKwh());
        surviving.setDistanceKm(sumNullable(earlier.getDistanceKm(), later.getDistanceKm(), 1));
        surviving.setLocationStartGeohash(earlier.getLocationStartGeohash());
        surviving.setLocationEndGeohash(later.getLocationEndGeohash());
        surviving.setOutsideTempCelsius(averageNullable(earlier.getOutsideTempCelsius(), later.getOutsideTempCelsius()));
        surviving.setEstimatedConsumedKwh(estimatedConsumedKwh);
        surviving.setRouteType(mergeRouteType(earlier.getRouteType(), later.getRouteType()));
        surviving.setFeedback(null);
        surviving.setRawPayload(buildMergedPayload(other.getId(), survivingTripId,
                earlier.getRawPayload(), later.getRawPayload()));
        surviving.setUserCreated(true);
        surviving.setUserEditedAt(OffsetDateTime.now());

        other.setDeletedAt(OffsetDateTime.now());
        tripRepository.save(other);

        return EvTripResponse.fromDomain(tripRepository.save(surviving));
    }

    private BigDecimal calculateEstimatedConsumedKwh(BigDecimal socStart, BigDecimal socEnd, UUID carId) {
        if (socStart == null || socEnd == null) return null;
        BigDecimal delta = socStart.subtract(socEnd);
        if (delta.compareTo(BigDecimal.ZERO) <= 0) return null;
        Car car = carRepository.findById(carId).orElse(null);
        if (car == null) return null;
        BigDecimal capacity = car.getEffectiveBatteryCapacityKwh();
        if (capacity == null) return null;
        return delta.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP)
                .multiply(capacity)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private String mergeRouteType(String a, String b) {
        if (a == null && b == null) return null;
        if (a == null) return b;
        if (b == null) return a;
        return a.equals(b) ? a : "COMBINED";
    }

    private BigDecimal sumNullable(BigDecimal a, BigDecimal b, int scale) {
        if (a == null && b == null) return null;
        if (a == null) return b;
        if (b == null) return a;
        return a.add(b).setScale(scale, RoundingMode.HALF_UP);
    }

    private BigDecimal averageNullable(BigDecimal a, BigDecimal b) {
        if (a == null && b == null) return null;
        if (a == null) return b;
        if (b == null) return a;
        return a.add(b).divide(new BigDecimal("2"), 1, RoundingMode.HALF_UP);
    }

    private String buildMergedPayload(UUID previousId, UUID currentId, String previousPayload, String currentPayload) {
        try {
            ObjectNode merged = objectMapper.createObjectNode();
            merged.putArray("merged_from").add(previousId.toString()).add(currentId.toString());
            if (previousPayload != null) merged.set("previous", objectMapper.readTree(previousPayload));
            if (currentPayload != null) merged.set("current", objectMapper.readTree(currentPayload));
            return objectMapper.writeValueAsString(merged);
        } catch (Exception e) {
            log.warn("Could not merge raw_payload: {}", e.getMessage());
            return null;
        }
    }

    private static final int MAX_TRIPS_PER_CAR = 500;

    @Transactional(readOnly = true)
    public List<EvTripResponse> getTripsForCar(UUID carId, User user) {
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new IllegalArgumentException("Car not found"));
        if (!car.getUserId().equals(user.getId())) {
            throw new IllegalArgumentException("Car not owned by user");
        }
        // Live trips are visible only if (a) the user holds the AutoSync-Live entitlement
        // AND (b) the car model is on the eligibility whitelist (Tesla + Polestar 2/4 today).
        // ADMIN / BETA_TESTER bypass (b) so they can audit data quality of blocked models.
        // Non-live sources (TESSIE, USER_CREATED, ...) are always returned to the owner.
        boolean canSeeLiveForThisCar = user.canViewLiveTrips()
                && (user.canBypassEligibilityGate() || TripDetectionEligibility.isEligible(car));
        List<EvTrip> trips = canSeeLiveForThisCar
                ? tripRepository.findByUserIdAndCarIdAndDeletedAtIsNullOrderByTripEndedAtDesc(
                        user.getId(), carId, PageRequest.of(0, MAX_TRIPS_PER_CAR))
                : tripRepository.findByUserIdAndCarIdExcludingSourcesAndDeletedAtIsNull(
                        user.getId(), carId, EvTrip.LIVE_TRIP_SOURCES, PageRequest.of(0, MAX_TRIPS_PER_CAR));
        return trips.stream().map(EvTripResponse::fromDomain).toList();
    }

    /**
     * Single source of truth for "may this user see / mutate this trip". Combines:
     *   1. data-source whitelist - non-live trips (TESSIE / USER_CREATED) are always
     *      visible to the owner regardless of subscription;
     *   2. subscription tier - live trips require AutoSync-Live or a privileged role;
     *   3. car-model eligibility - even Tier-2 customers see live trips only for
     *      whitelisted models (Tesla, Polestar 2/4) unless they hold ADMIN/BETA_TESTER.
     *
     * Performs a Car lookup only when steps 1+2 already passed and the car-model
     * gate matters. Tessie / USER_CREATED edits skip the DB hit entirely.
     */
    private boolean userCanSeeTrip(EvTrip trip, User user) {
        if (!trip.isLiveSource()) return true;
        if (!user.canViewLiveTrips()) return false;
        if (user.canBypassEligibilityGate()) return true;
        Car car = carRepository.findById(trip.getCarId()).orElse(null);
        return TripDetectionEligibility.isEligible(car);
    }
}
