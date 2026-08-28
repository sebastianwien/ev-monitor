package com.evmonitor.infrastructure.route;

import ch.hsr.geohash.GeoHash;
import ch.hsr.geohash.WGS84Point;
import com.evmonitor.domain.EvTripRepository;
import com.evmonitor.domain.route.EncodedPolyline;
import com.evmonitor.domain.route.RouteSketch;
import com.evmonitor.domain.route.RouteSketchRepository;
import com.evmonitor.domain.route.RouteSketcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter zum {@link RouteSketcher}-Port: fragt den Router und legt das Ergebnis an der Fahrt ab.
 *
 * <p>Zwei Wege fuehren zu einer Linie. Die <b>Skizze</b> verbindet Start- und Zielgegend und
 * weiss nichts davon, wie die Fahrt wirklich verlief; sie wird auf dem Geohash-Paar gecacht,
 * weil zwei Fahrten derselben Relation denselben Weg ergeben und Pendler diese Relation
 * taeglich fahren. Das <b>Matching</b> legt dagegen die gefahrene Spur auf das Strassennetz -
 * ihre Stuetzpunkte sind fahrtspezifisch, ein Cache-Treffer waere hier die Spur einer anderen
 * Fahrt, deshalb geht dieser Weg am Cache vorbei.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RouteSketchService implements RouteSketcher {

    /** Herkunft einer Linie, wie sie in {@code ev_trip.route_kind} steht. */
    static final String KIND_SKETCH  = "SKETCH";
    static final String KIND_MATCHED = "MATCHED";

    /**
     * Wie weit die gerechnete Linie von der gemessenen Fahrleistung abweichen darf. Nach oben
     * grosszuegiger, weil die Spur Kurven abschneidet, die die Strasse mitmacht; nach unten
     * enger, weil eine deutlich kuerzere Route bedeutet, dass der Router einen anderen Weg
     * genommen hat als das Auto.
     */
    private static final double MAX_LONGER  = 1.5;
    private static final double MAX_SHORTER = 0.7;

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
                tripRepository.updateRoutePolyline(tripId, cached.get(), KIND_SKETCH);
                return;
            }

            WGS84Point start = GeoHash.fromGeohashString(startGeohash).getBoundingBoxCenter();
            WGS84Point end = GeoHash.fromGeohashString(endGeohash).getBoundingBoxCenter();
            Optional<OpenRouteServiceClient.Route> route = client.route(
                    start.getLatitude(), start.getLongitude(), end.getLatitude(), end.getLongitude());
            if (route.isEmpty()) {
                return;
            }

            String polyline = route.get().polyline();
            tripRepository.updateRoutePolyline(tripId, polyline, KIND_SKETCH);
            sketchRepository.save(new RouteSketch(startGeohash, endGeohash, polyline, LocalDateTime.now()));
            log.debug("Route fuer Trip {} berechnet ({} -> {})", tripId, startGeohash, endGeohash);
        } catch (Exception e) {
            // Best-effort wie bei der Temperatur: ohne Linie faellt die Kachel auf die
            // Luftlinie zurueck, die Fahrt selbst bleibt unberuehrt.
            log.warn("Routenberechnung fuer Trip {} fehlgeschlagen: {}", tripId, e.getMessage());
        }
    }

    @Async
    @Override
    @Transactional
    public void matchTrace(UUID tripId, String tracePolyline, BigDecimal distanceKm) {
        if (isBlank(tracePolyline) || distanceKm == null || distanceKm.signum() <= 0) {
            return;
        }
        try {
            // Der Router faehrt jeden uebergebenen Punkt an, nimmt aber hoechstens 50 davon.
            // Ausgeduennt wird gleichmaessig: die Form der Fahrt bleibt erhalten, nur ihre
            // Aufloesung sinkt.
            List<double[]> waypoints = EncodedPolyline.thin(
                    EncodedPolyline.decode(tracePolyline), OpenRouteServiceClient.MAX_WAYPOINTS);
            if (waypoints.size() < 2) {
                return;
            }
            client.route(waypoints).ifPresent(route -> {
                double routeKm = route.distanceMeters() / 1000.0;
                if (!fitsTheDrive(routeKm, distanceKm.doubleValue())) {
                    log.info("Gematchte Linie fuer Trip {} verworfen: {} km gerechnet gegen {} km gefahren",
                            tripId, Math.round(routeKm), distanceKm);
                    return;
                }
                tripRepository.updateRoutePolyline(tripId, route.polyline(), KIND_MATCHED);
                log.debug("Spur von Trip {} auf {} Stuetzpunkten gematcht", tripId, waypoints.size());
            });
        } catch (Exception e) {
            // Best-effort: ohne gematchte Linie zeigt die Karte weiterhin die rohe Spur.
            log.warn("Map-Matching fuer Trip {} fehlgeschlagen: {}", tripId, e.getMessage());
        }
    }

    /**
     * Ob die gerechnete Linie zur gefahrenen Strecke passt. Ohne Laengenangabe des Routers
     * gibt es nichts zu vergleichen - dann bleibt die rohe Spur stehen, die immerhin gemessen
     * ist, statt eine ungepruefte Linie als Fahrt auszugeben.
     */
    private static boolean fitsTheDrive(double routeKm, double drivenKm) {
        if (routeKm <= 0) return false;
        return routeKm <= drivenKm * MAX_LONGER && routeKm >= drivenKm * MAX_SHORTER;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
