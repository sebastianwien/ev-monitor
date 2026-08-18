package com.evmonitor.infrastructure.route;

import ch.hsr.geohash.GeoHash;
import ch.hsr.geohash.WGS84Point;
import com.evmonitor.domain.EvTripRepository;
import com.evmonitor.domain.route.RouteSketch;
import com.evmonitor.domain.route.RouteSketchRepository;
import com.evmonitor.domain.route.RouteSketcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter zum {@link RouteSketcher}-Port: fragt den Router und legt das Ergebnis an der Fahrt
 * sowie im Cache ab.
 *
 * <p>Gecacht wird auf dem Geohash-Paar, nicht pro Fahrt. Zwei Fahrten derselben Relation
 * ergeben denselben Weg, und Pendler fahren diese Relation taeglich - ohne Cache waere jede
 * Fahrt ein eigener Aufruf.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RouteSketchService implements RouteSketcher {

    private final OpenRouteServiceClient client;
    private final RouteSketchRepository sketchRepository;
    private final EvTripRepository tripRepository;

    @Async
    @Override
    @Transactional
    public void sketchTrip(UUID tripId, String startGeohash, String endGeohash) {
        if (isBlank(startGeohash) || isBlank(endGeohash)) {
            return;
        }
        // Rundfahrt: beide Enden liegen in derselben Zelle. Eine Route zwischen einem Punkt
        // und sich selbst hat keine Aussage - die Kachel zeigt dann nur die Flaeche.
        if (startGeohash.equals(endGeohash)) {
            return;
        }
        try {
            Optional<String> cached = sketchRepository
                    .findByStartGeohashAndEndGeohash(startGeohash, endGeohash)
                    .map(RouteSketch::getPolyline);
            if (cached.isPresent()) {
                tripRepository.updateRoutePolyline(tripId, cached.get());
                return;
            }

            WGS84Point start = GeoHash.fromGeohashString(startGeohash).getBoundingBoxCenter();
            WGS84Point end = GeoHash.fromGeohashString(endGeohash).getBoundingBoxCenter();
            Optional<String> polyline = client.route(
                    start.getLatitude(), start.getLongitude(), end.getLatitude(), end.getLongitude());
            if (polyline.isEmpty()) {
                return;
            }

            tripRepository.updateRoutePolyline(tripId, polyline.get());
            sketchRepository.save(new RouteSketch(startGeohash, endGeohash, polyline.get(), LocalDateTime.now()));
            log.debug("Route fuer Trip {} berechnet ({} -> {})", tripId, startGeohash, endGeohash);
        } catch (Exception e) {
            // Best-effort wie bei der Temperatur: ohne Linie faellt die Kachel auf die
            // Luftlinie zurueck, die Fahrt selbst bleibt unberuehrt.
            log.warn("Routenberechnung fuer Trip {} fehlgeschlagen: {}", tripId, e.getMessage());
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
