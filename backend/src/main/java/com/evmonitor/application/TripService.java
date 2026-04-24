package com.evmonitor.application;

import com.evmonitor.domain.EvTrip;
import com.evmonitor.domain.EvTripRepository;
import com.evmonitor.domain.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TripService {

    private final EvTripRepository tripRepository;

    @Transactional
    public UUID saveTrip(InternalTripRequest req) {
        if (req.userId() == null) {
            throw new ValidationException("userId is required");
        }
        if (req.externalId() != null) {
            var existing = tripRepository.findByExternalId(req.externalId());
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
                .nominalFullPackKwh(req.nominalFullPackKwh())
                .estimatedConsumedKwh(req.estimatedConsumedKwh())
                .status(req.status() != null ? req.status() : "COMPLETED")
                .rawPayload(req.rawPayload())
                .build();

        EvTrip saved = tripRepository.save(trip);
        log.info("Trip saved: id={} externalId={} car={} distance={} km",
                saved.getId(), req.externalId(), req.carId(), req.distanceKm());
        return saved.getId();
    }
}
