package com.evmonitor.application.ingest;

import com.evmonitor.application.InternalTripRequest;
import com.evmonitor.application.ingest.api.InternalIngestRequest;
import com.evmonitor.application.ingest.api.InternalIngestRequest.ChargingSession;
import com.evmonitor.application.ingest.api.InternalIngestRequest.Trip;
import com.evmonitor.application.ingest.api.InternalIngestResponse;
import com.evmonitor.application.ingest.api.InternalIngestResponse.EntryResult;
import com.evmonitor.domain.EvTripRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Adapter für {@code POST /api/internal/ingest} (Vertrag B) vor dem {@link IngestGateway}. Regeln wie
 * die Connector-Tür ({@link IngestDoor#CONNECTOR_PUSH}), weil hier dieselben Absender ankommen.
 *
 * <p>Bewusst ohne {@code @Transactional}: jeder Eintrag ist ein eigener Gateway-Aufruf, also eine
 * eigene Transaktion. Nur so stimmt die Antwort je Eintrag, und nur außerhalb der Transaktion lässt
 * sich ein Race (parallel angelegt, Unique-Constraint am Commit) als Duplikat erkennen. Scheitert ein
 * Eintrag unerwartet, bleiben die davor angelegten stehen; eine Wiederholung ist sicher, weil die
 * Dedup sie wiedererkennt.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IngestApiService {

    private final IngestGateway gateway;
    private final EvTripRepository tripRepository;

    /**
     * @throws com.evmonitor.domain.exception.NotFoundException  Auto unbekannt oder gelöscht
     * @throws com.evmonitor.domain.exception.ForbiddenException Auto gehört nicht dem Nutzer
     */
    public InternalIngestResponse ingest(InternalIngestRequest request) {
        log.info("Ingest {} car={} sessions={} trips={} ref={}", request.dataSource(), request.carId(),
                request.chargingSessionsOrEmpty().size(), request.tripsOrEmpty().size(), request.externalRef());
        List<EntryResult> sessions = request.chargingSessionsOrEmpty().stream()
                .map(session -> ingestCharging(request, session)).toList();
        List<EntryResult> trips = request.tripsOrEmpty().stream()
                .map(trip -> ingestTrip(request, trip)).toList();
        return new InternalIngestResponse(sessions, trips);
    }

    private EntryResult ingestCharging(InternalIngestRequest request, ChargingSession session) {
        try {
            IngestResult result = gateway.ingestCharging(new IngestCommand(request.userId(), request.carId(),
                    request.dataSource(), IngestDoor.CONNECTOR_PUSH, List.of(toEntry(session))));
            return result.created().isEmpty()
                    ? EntryResult.duplicate(null)
                    : EntryResult.created(result.created().get(0).getId());
        } catch (DataIntegrityViolationException e) {
            log.debug("Ingest {}: Ladung parallel angelegt (race) - Duplikat", request.dataSource());
            return EntryResult.duplicate(null);
        }
    }

    private EntryResult ingestTrip(InternalIngestRequest request, Trip trip) {
        try {
            IngestGateway.TripIngest result = gateway.ingestTrip(toTripRequest(request, trip));
            return result.created() ? EntryResult.created(result.id()) : EntryResult.duplicate(result.id());
        } catch (DataIntegrityViolationException e) {
            log.debug("Ingest {}: Fahrt {} parallel angelegt (race) - Duplikat", request.dataSource(), trip.externalId());
            return tripRepository.findByExternalId(trip.externalId())
                    .map(existing -> EntryResult.duplicate(existing.getId()))
                    .orElseThrow(() -> e);
        }
    }

    private static ChargingEntry toEntry(ChargingSession s) {
        return ChargingEntry.builder()
                .loggedAt(s.loggedAt())
                .kwhCharged(s.kwhCharged())
                .energySource(LenientEnums.energySource(s.energySource()))
                .costEur(s.costEur())
                .pricePerKwh(s.pricePerKwh())
                .chargeDurationMinutes(s.chargeDurationMinutes())
                .geohash(s.geohash())
                .publicCharging(s.publicCharging())
                .cpoName(s.cpoName())
                .odometerKm(s.odometerKm())
                .odometerSuggestionMinKm(s.odometerSuggestionMinKm())
                .odometerSuggestionMaxKm(s.odometerSuggestionMaxKm())
                .maxChargingPowerKw(s.maxChargingPowerKw())
                .socBefore(s.socBefore())
                .socAfter(s.socAfter())
                .socStartMissed(s.socStartMissed())
                .chargingType(LenientEnums.chargingType(s.chargingType()))
                .temperatureCelsius(s.temperatureCelsius())
                .rawImportData(s.rawImportData())
                .powerCurvePointsJson(s.extras() != null ? s.extras().powerCurvePointsJson() : null)
                .socCurvePointsJson(s.extras() != null ? s.extras().socCurvePointsJson() : null)
                .build();
    }

    private static InternalTripRequest toTripRequest(InternalIngestRequest request, Trip t) {
        return InternalTripRequest.builder()
                .externalId(t.externalId())
                .carId(request.carId())
                .userId(request.userId())
                .dataSource(request.dataSource().name())
                .tripStartedAt(t.tripStartedAt())
                .tripEndedAt(t.tripEndedAt())
                .socStart(t.socStart())
                .socEnd(t.socEnd())
                .odometerStartKm(t.odometerStartKm())
                .odometerEndKm(t.odometerEndKm())
                .distanceKm(t.distanceKm())
                .locationStartGeohash(t.locationStartGeohash())
                .locationEndGeohash(t.locationEndGeohash())
                .outsideTempCelsius(t.outsideTempCelsius())
                .energyRemainingStartKwh(t.energyRemainingStartKwh())
                .energyRemainingEndKwh(t.energyRemainingEndKwh())
                .estimatedConsumedKwh(t.estimatedConsumedKwh())
                .avgSpeedKmh(t.avgSpeedKmh())
                .maxSpeedKmh(t.maxSpeedKmh())
                .status(t.status())
                .rawPayload(t.rawPayload())
                .telemetryExtras(t.telemetryExtras())
                .tracePolyline(t.tracePolyline())
                .build();
    }
}
