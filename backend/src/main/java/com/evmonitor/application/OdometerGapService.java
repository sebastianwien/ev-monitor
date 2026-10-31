package com.evmonitor.application;

import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.EvTripRepository;
import com.evmonitor.domain.TripOdometerSpan;
import com.evmonitor.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Lücken im Kilometerstand zwischen zwei aufgezeichneten Fahrten, beim Lesen abgeleitet und nicht
 * gespeichert. Löschen, Wiederherstellen, Zusammenführen oder ein später Import wirken sofort.
 *
 * <p>Regel: Fahrten mit Stand am Start und Ende, nach Start sortiert, aus allen Quellen. Ein
 * Maximum der bisherigen Endstände läuft mit, damit überlappende Quellen (Tessie und Tesla live)
 * keine Lücke erzeugen. Lücke = Startstand minus Maximum, ab 1,0 km. Gezeigt nur, wenn die Fahrt
 * danach aus einer Quelle mit {@link com.evmonitor.domain.DataSource#showsOdometerGaps()} kommt
 * und die Zeit für die Strecke reicht (höchstens 150 km/h im Schnitt, keine Zeitüberlappung).
 */
@Service
@RequiredArgsConstructor
public class OdometerGapService {

    /** Wie im Connector: Rauschen im Stand liegt bei Prod-Daten bis 0,3 km. */
    static final BigDecimal MIN_GAP_KM = new BigDecimal("1.0");

    /** Schneller ist keine Fahrt, sondern ein Fehler im Kilometerstand. */
    static final double MAX_PLAUSIBLE_AVG_KMH = 150.0;

    private final CarRepository carRepository;
    private final EvTripRepository tripRepository;

    /**
     * Lücken, deren Ende in [from, to] liegt. Gerechnet wird über dieselben Fahrten, die der
     * Nutzer im Logbuch sieht: verborgene Live-Fahrten füllen keine Lücke und begrenzen keine,
     * sonst stünden ihre Zeiten in der Antwort.
     *
     * @throws IllegalArgumentException wenn das Auto fehlt oder nicht dem Nutzer gehört
     */
    @Transactional(readOnly = true)
    public List<OdometerGap> getGapsForCar(UUID carId, User user, OffsetDateTime from, OffsetDateTime to) {
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new IllegalArgumentException("Car not found"));
        if (!car.isOwnedBy(user.getId())) {
            throw new IllegalArgumentException("Car not owned by user");
        }
        boolean canSeeLive = TripService.canSeeLiveTrips(user, car);
        List<TripOdometerSpan> visible = tripRepository.findOdometerSpansStartedUntil(user.getId(), carId, to)
                .stream()
                .filter(span -> canSeeLive || !span.isLiveSource())
                .toList();
        return detect(visible, from, to);
    }

    /** Reine Funktion: Lücken vor den Fahrten, die in [from, to] beginnen. Frühere Fahrten zählen als Anker. */
    static List<OdometerGap> detect(List<TripOdometerSpan> spans, OffsetDateTime from, OffsetDateTime to) {
        List<TripOdometerSpan> sorted = spans.stream()
                .filter(s -> s.startedAt() != null && s.odometerStartKm() != null && s.odometerEndKm() != null)
                .sorted(Comparator.comparing(TripOdometerSpan::startedAt))
                .toList();

        List<OdometerGap> gaps = new ArrayList<>();
        BigDecimal maxOdometerEnd = null;
        OffsetDateTime lastEndedAt = null;
        for (TripOdometerSpan trip : sorted) {
            if (maxOdometerEnd != null && isInWindow(trip.startedAt(), from, to) && trip.showsOdometerGapBefore()) {
                BigDecimal km = trip.odometerStartKm().subtract(maxOdometerEnd);
                if (km.compareTo(MIN_GAP_KM) >= 0 && isDrivable(km, lastEndedAt, trip.startedAt())) {
                    gaps.add(new OdometerGap(lastEndedAt, trip.startedAt(), km.setScale(1, RoundingMode.HALF_UP)));
                }
            }
            maxOdometerEnd = maxOdometerEnd == null ? trip.odometerEndKm() : maxOdometerEnd.max(trip.odometerEndKm());
            OffsetDateTime ended = trip.endedAt() != null ? trip.endedAt() : trip.startedAt();
            if (lastEndedAt == null || ended.isAfter(lastEndedAt)) lastEndedAt = ended;
        }
        return gaps;
    }

    private static boolean isInWindow(OffsetDateTime t, OffsetDateTime from, OffsetDateTime to) {
        return !t.isBefore(from) && !t.isAfter(to);
    }

    /** Reicht die Zeit zwischen den Fahrten für die Strecke? Zeitüberlappung reicht nie. */
    private static boolean isDrivable(BigDecimal km, OffsetDateTime gapFrom, OffsetDateTime gapTo) {
        if (!gapFrom.isBefore(gapTo)) return false;
        double hours = Duration.between(gapFrom, gapTo).toMillis() / 3_600_000.0;
        return km.doubleValue() / hours <= MAX_PLAUSIBLE_AVG_KMH;
    }
}
