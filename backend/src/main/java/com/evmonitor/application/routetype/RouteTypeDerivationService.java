package com.evmonitor.application.routetype;

import com.evmonitor.domain.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Leitet den Streckentyp einer Ladung ab und schreibt ihn, wenn die Rangfolge es erlaubt
 * (USER > SOURCE > TRIPS > HEURISTIC). Quelle 1: Fahrten desselben Autos zwischen Vorladung und
 * dieser Ladung, egal aus welcher Quelle die Ladung oder die Fahrten stammen. Quelle 2, nur ohne
 * Fahrten und nur auf leeren Streckentyp: Faustregel aus der mittleren Entladeleistung (ab 25 kW Autobahn).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RouteTypeDerivationService {

    /** Smartcar-Fahrtzeiten sind unzuverlässig (21,6 % mit über 140 km/h Schnitt), bis repariert ausgeschlossen. */
    static final String EXCLUDED_TRIP_SOURCE = EvTrip.DATA_SOURCE_SMARTCAR_LIVE;

    private final EvLogRepository evLogRepository;
    private final EvTripRepository evTripRepository;
    private final CarRepository carRepository;

    public record Derivation(RouteType routeType, RouteTypeSource source) {}

    /** Ableitung für eine Ladung berechnen und bei Erlaubnis speichern. Leer, wenn nichts geschrieben wurde. */
    @Transactional
    public Optional<Derivation> derive(UUID logId) {
        Optional<EvLog> current = evLogRepository.findById(logId);
        if (current.isEmpty()) return Optional.empty();
        Optional<Derivation> result = compute(current.get());
        result.ifPresent(d -> {
            evLogRepository.updateRouteType(logId, d.routeType(), d.source());
            log.debug("Streckentyp {} ({}) für Ladung {} abgeleitet", d.routeType(), d.source(), logId);
        });
        return result;
    }

    /** Nur rechnen, nichts schreiben (für Dry-run und Tests). */
    public Optional<Derivation> compute(EvLog current) {
        Optional<EvLog> previous = evLogRepository.findPreviousByCarId(current.getCarId(), current.getLoggedAt());
        if (previous.isEmpty()) return Optional.empty();
        Optional<Car> car = carRepository.findById(current.getCarId());
        if (car.isEmpty()) return Optional.empty();

        if (current.mayOverrideRouteType(RouteTypeSource.TRIPS)) {
            Optional<RouteType> fromTrips = RouteTypeRules.fromTrips(previous.get(), current, tripsBetween(previous.get(), current));
            if (fromTrips.isPresent()) return Optional.of(new Derivation(fromTrips.get(), RouteTypeSource.TRIPS));
        }
        // Faustregel nur, wenn gar kein Streckentyp da ist: ein geerbtes COMBINED bleibt stehen.
        if (current.getRouteType() == null) {
            return RouteTypeRules.fromMeanPower(previous.get(), current, car.get())
                    .map(type -> new Derivation(type, RouteTypeSource.HEURISTIC));
        }
        return Optional.empty();
    }

    private List<EvTrip> tripsBetween(EvLog previous, EvLog current) {
        OffsetDateTime from = RouteTypeRules.chargeEndUtc(previous);
        OffsetDateTime to = current.getLoggedAt().atOffset(ZoneOffset.UTC);
        if (!from.isBefore(to)) return List.of();
        return evTripRepository.findByCarIdAndTripStartedAtBetweenOrderByTripStartedAtAsc(current.getCarId(), from, to)
                .stream()
                .filter(t -> !EXCLUDED_TRIP_SOURCE.equals(t.getDataSource()))
                .filter(t -> t.getTripEndedAt() != null && !t.getTripEndedAt().isAfter(to))
                .toList();
    }
}
