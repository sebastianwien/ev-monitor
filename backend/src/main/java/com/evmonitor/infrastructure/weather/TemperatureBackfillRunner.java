package com.evmonitor.infrastructure.weather;

import ch.hsr.geohash.GeoHash;
import ch.hsr.geohash.WGS84Point;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fuehrt einen Temperatur-Backfill gegen Open-Meteo aus - fuer Ladelogs wie fuer Fahrten.
 *
 * <p>Was hier liegt, brauchen beide gleich:
 * <ul>
 *   <li><b>Buendelung:</b> ein Abruf liefert einen ganzen Tag fuer einen Ort. Zeilen am selben Ort
 *       und Tag teilen sich diesen Abruf, was die Zahl der Anfragen etwa halbiert. Der Ort wird
 *       dafuer auf {@value #LOCATION_PRECISION} Geohash-Stellen gekuerzt (~5 km) - das Wettermodell
 *       loest ohnehin nur 1-11 km auf.</li>
 *   <li><b>Budget:</b> die freie Open-Meteo-API erlaubt 600 Anfragen/min, 5.000/h und 10.000/Tag.
 *       Ein Lauf bekommt daher eine Obergrenze, eine Pause zwischen den Abrufen und eine Laufzeit.</li>
 *   <li><b>Abbruch:</b> nach mehreren Fehlern in Folge (429, Ausfall, Timeout) endet der Lauf,
 *       statt stundenlang gegen eine geschlossene Tuer zu laufen. Die naechste Nacht macht weiter.</li>
 * </ul>
 *
 * <p>Fortschritt wird pro Zeile geschrieben, nicht am Ende: stirbt der Lauf mitten drin, setzt der
 * naechste genau dort auf. Es gibt keinen Cursor und keinen Zwischenstand, der auseinanderlaufen
 * koennte - der Arbeitsvorrat ergibt sich jedes Mal neu aus der Datenbank.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TemperatureBackfillRunner {

    /** ~5 km - feiner als das Wettermodell aufloest, gruende genug zum Buendeln. */
    static final int LOCATION_PRECISION = 5;

    private final TemperatureService temperatureService;

    /** Nur ein Backfill gleichzeitig - sonst teilen sich zwei Laeufe dasselbe API-Kontingent. */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /** Ein Ort mit Zeitpunkt, fuer den ein Wert gebraucht wird. */
    public record Point(String geohash, LocalDateTime at) {}

    /** Eine zu fuellende Zeile. Fahrten bringen zwei Punkte mit (Start und Ende), Logs einen. */
    public record Candidate(UUID id, List<Point> points) {}

    /**
     * @param maxApiCalls          Obergrenze der Wetterabrufe fuer diesen Lauf
     * @param sleepMillis          Pause zwischen zwei Abrufen
     * @param maxConsecutiveErrors so viele Fehler in Folge beenden den Lauf
     * @param maxDuration          Zeitbudget, damit ein zaeher Lauf nicht in den Tagesbetrieb laeuft
     */
    public record Budget(int maxApiCalls, long sleepMillis, int maxConsecutiveErrors, Duration maxDuration) {}

    public record Summary(int enriched, int skipped, int apiCalls, String stopReason) {
        @Override
        public String toString() {
            return "%d enriched, %d skipped, %d api calls (%s)".formatted(enriched, skipped, apiCalls, stopReason);
        }
    }

    /** Nimmt den ermittelten Wert fuer eine Zeile entgegen und schreibt ihn weg. */
    @FunctionalInterface
    public interface Sink {
        void accept(UUID id, double celsius);
    }

    public Summary run(String label, List<Candidate> candidates, Sink sink, Budget budget) {
        if (!running.compareAndSet(false, true)) {
            log.info("Temperature backfill [{}] skipped - another run is still active", label);
            return new Summary(0, 0, 0, "already running");
        }
        try {
            return doRun(label, candidates, sink, budget);
        } finally {
            running.set(false);
        }
    }

    private Summary doRun(String label, List<Candidate> candidates, Sink sink, Budget budget) {
        Map<DayAtLocation, List<Double>> hourlyCache = new HashMap<>();
        Instant deadline = Instant.now().plus(budget.maxDuration());
        int enriched = 0;
        int skipped = 0;
        int apiCalls = 0;
        int consecutiveErrors = 0;
        String stopReason = "done";

        for (Candidate candidate : candidates) {
            if (Instant.now().isAfter(deadline)) {
                stopReason = "time budget reached";
                break;
            }

            double sum = 0;
            int found = 0;
            boolean aborted = false;

            for (Point point : candidate.points()) {
                DayAtLocation key = DayAtLocation.of(point);
                if (key == null) continue;

                if (!hourlyCache.containsKey(key)) {
                    if (apiCalls >= budget.maxApiCalls()) {
                        stopReason = "call budget reached";
                        aborted = true;
                        break;
                    }
                    apiCalls++;
                    try {
                        hourlyCache.put(key, temperatureService.getHourlyForUtcDay(
                                key.latitude(), key.longitude(), key.utcDate()));
                        consecutiveErrors = 0;
                    } catch (Exception e) {
                        consecutiveErrors++;
                        log.warn("Temperature backfill [{}]: fetch failed for {} ({}), {} in a row",
                                label, key, e.getMessage(), consecutiveErrors);
                        if (consecutiveErrors >= budget.maxConsecutiveErrors()) {
                            stopReason = "too many consecutive errors";
                        }
                        aborted = true;
                        break;
                    } finally {
                        pause(budget.sleepMillis());
                    }
                }

                Double value = valueAt(hourlyCache.get(key), key.utcHour());
                if (value != null) {
                    sum += value;
                    found++;
                }
            }

            if (aborted) {
                if ("too many consecutive errors".equals(stopReason) || "call budget reached".equals(stopReason)) break;
                skipped++;
                continue;
            }

            if (found == 0) {
                skipped++;
                continue;
            }
            sink.accept(candidate.id(), sum / found);
            enriched++;
        }

        Summary summary = new Summary(enriched, skipped, apiCalls, stopReason);
        log.info("Temperature backfill [{}]: {}", label, summary);
        return summary;
    }

    private static Double valueAt(List<Double> hourly, int hour) {
        if (hourly == null || hour >= hourly.size()) return null;
        return hourly.get(hour);
    }

    private void pause(long millis) {
        if (millis <= 0) return;
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Ort (gekuerzt) plus UTC-Tag - der Schluessel, unter dem sich Zeilen einen Abruf teilen.
     * Die Stunde gehoert nicht zum Schluessel, sie waehlt nur den Wert aus dem Tagesergebnis.
     */
    private record DayAtLocation(String geohash, LocalDate utcDate, int utcHour) {

        static DayAtLocation of(Point point) {
            if (point.geohash() == null || point.geohash().isBlank() || point.at() == null) return null;
            String coarse = point.geohash().length() > LOCATION_PRECISION
                    ? point.geohash().substring(0, LOCATION_PRECISION)
                    : point.geohash();
            ZonedDateTime utc = point.at().atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC);
            return new DayAtLocation(coarse, utc.toLocalDate(), utc.getHour());
        }

        /** Nur Ort und Tag bestimmen die Identitaet - die Stunde kommt aus demselben Tagesergebnis. */
        @Override
        public boolean equals(Object o) {
            return o instanceof DayAtLocation other
                    && geohash.equals(other.geohash) && utcDate.equals(other.utcDate);
        }

        @Override
        public int hashCode() {
            return geohash.hashCode() * 31 + utcDate.hashCode();
        }

        @Override
        public String toString() {
            return geohash + "@" + utcDate;
        }

        double latitude() {
            return center().getLatitude();
        }

        double longitude() {
            return center().getLongitude();
        }

        private WGS84Point center() {
            return GeoHash.fromGeohashString(geohash).getBoundingBoxCenter();
        }
    }
}
