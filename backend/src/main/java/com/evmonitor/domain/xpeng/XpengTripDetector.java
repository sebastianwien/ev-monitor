package com.evmonitor.domain.xpeng;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Stateful trip detector for the XPeng 5s-telematics stream. Feed rows in order
 * via {@link #consume(XpengTelematicsRow)}; the detector emits a {@link DetectedTrip}
 * when a trip ends. Call {@link #finish()} once at end-of-stream to flush any
 * trip still open.
 *
 * Trip semantics:
 *   - starts on the first row that shows motion - gear ∈ {D,R} OR speed &gt; 0 -
 *     unless the row is explicitly in Park. Gear- und Speed-Modul schlafen
 *     unabhaengig, daher genuegt eines von beiden als Bewegungsindiz.
 *   - continues through brief stops while still in gear (e.g. traffic lights) and
 *     through data gaps: beim Merge der CSV-Cluster ueber {@code timer} kann ein
 *     Zeitpunkt nur im power-Stream liegen - gear ist dann null. So ein Luecken-
 *     Sample ist kein Park und darf die Fahrt nicht beenden.
 *   - ends only on an explicit Park (gear == P), or at end-of-stream via {@link #finish()}.
 *
 * Trips below {@code MIN_DISTANCE_KM} are discarded (parking-lot maneuvers).
 */
public class XpengTripDetector {

    private static final BigDecimal MIN_DISTANCE_KM = new BigDecimal("0.1");
    private static final Duration MAX_INTEGRATION_GAP = Duration.ofSeconds(60);

    private State state = State.IDLE;
    private LocalDateTime startedAt;
    private LocalDateTime lastDrivingAt;
    private BigDecimal odometerStart;
    private BigDecimal odometerLast;
    private BigDecimal socStart;
    private BigDecimal socLast;
    private BigDecimal maxSpeed = BigDecimal.ZERO;
    private BigDecimal speedSum = BigDecimal.ZERO;
    private long speedSamples = 0;
    private BigDecimal energyAccumWh = BigDecimal.ZERO;
    private LocalDateTime prevSampleTime;
    private BigDecimal prevPowerW;
    private TripExtrasAggregator extrasAggregator;

    // Rolling anchor of the most recent row we've seen, used to back-date trip start
    // when the gear change between samples loses a few hundred meters.
    private BigDecimal pendingStartOdometer;
    private BigDecimal pendingStartSoc;

    private enum State { IDLE, DRIVING }

    public Optional<DetectedTrip> consume(XpengTelematicsRow row) {
        if (row == null || row.timer() == null) return Optional.empty();

        return switch (state) {
            case IDLE -> handleIdle(row);
            case DRIVING -> handleDriving(row);
        };
    }

    private Optional<DetectedTrip> handleIdle(XpengTelematicsRow row) {
        // Bewegung startet die Fahrt: Fahrgang ODER Geschwindigkeit. Ein explizites
        // P dominiert aber - eine stale Speed-Anzeige bei gear==P ist keine Fahrt.
        if (!row.isParked() && (row.isDriving() || row.isMoving())) {
            startTrip(row);
        } else {
            // Track the last known parked position as anchor for the next trip start.
            if (row.odometerKm() != null) pendingStartOdometer = row.odometerKm();
            if (row.socDisplay() != null) pendingStartSoc = row.socDisplay();
        }
        return Optional.empty();
    }

    private Optional<DetectedTrip> handleDriving(XpengTelematicsRow row) {
        // Nur ein explizites Park (gear == P) beendet die Fahrt. Ein gear-loses
        // Sample ist eine Merge-Datenluecke (power-only Zeitpunkt), kein Park -
        // sonst zerfaellt die Fahrt in Fragmente.
        if (row.isParked()) {
            DetectedTrip emitted = finalizeTrip(row);
            state = State.IDLE;
            return Optional.ofNullable(emitted);
        }

        // In gear, rollend oder Datenluecke - weiterfuehren. accumulate() ist
        // null-fest und ueberspringt fehlende Felder.
        accumulate(row);
        return Optional.empty();
    }

    public Optional<DetectedTrip> finish() {
        if (state == State.DRIVING) {
            DetectedTrip emitted = finalizeTrip(null);
            state = State.IDLE;
            return Optional.ofNullable(emitted);
        }
        return Optional.empty();
    }

    private void startTrip(XpengTelematicsRow row) {
        state = State.DRIVING;
        startedAt = row.timer();
        lastDrivingAt = row.timer();
        // Prefer the last-parked anchor (captures distance lost during gear-shift),
        // fall back to this row's value when no anchor was seen.
        odometerStart = pendingStartOdometer != null ? pendingStartOdometer : row.odometerKm();
        odometerLast = row.odometerKm();
        socStart = pendingStartSoc != null ? pendingStartSoc : row.socDisplay();
        socLast = row.socDisplay();
        pendingStartOdometer = null;
        pendingStartSoc = null;
        maxSpeed = nonNull(row.vehSpeedKmh());
        speedSum = nonNull(row.vehSpeedKmh());
        speedSamples = row.vehSpeedKmh() == null ? 0 : 1;
        energyAccumWh = BigDecimal.ZERO;
        prevSampleTime = row.timer();
        prevPowerW = currentPowerW(row);
        extrasAggregator = new TripExtrasAggregator();
        extrasAggregator.consume(row);
    }

    private void accumulate(XpengTelematicsRow row) {
        lastDrivingAt = row.timer();
        if (extrasAggregator != null) extrasAggregator.consume(row);
        if (row.odometerKm() != null) odometerLast = row.odometerKm();
        if (row.socDisplay() != null) socLast = row.socDisplay();
        if (row.vehSpeedKmh() != null) {
            if (row.vehSpeedKmh().compareTo(maxSpeed) > 0) maxSpeed = row.vehSpeedKmh();
            speedSum = speedSum.add(row.vehSpeedKmh());
            speedSamples++;
        }
        // Trapezoidal integration over (volt*current) → Wh
        BigDecimal nowPowerW = currentPowerW(row);
        if (prevSampleTime != null && nowPowerW != null && prevPowerW != null) {
            Duration dt = Duration.between(prevSampleTime, row.timer());
            if (!dt.isNegative() && dt.compareTo(MAX_INTEGRATION_GAP) <= 0) {
                BigDecimal avgPowerW = prevPowerW.add(nowPowerW).divide(BigDecimal.valueOf(2), MathContext.DECIMAL64);
                BigDecimal dtHours = BigDecimal.valueOf(dt.toMillis()).divide(BigDecimal.valueOf(3_600_000), MathContext.DECIMAL64);
                energyAccumWh = energyAccumWh.add(avgPowerW.multiply(dtHours, MathContext.DECIMAL64));
            }
        }
        if (nowPowerW != null) prevPowerW = nowPowerW;
        prevSampleTime = row.timer();
    }

    private DetectedTrip finalizeTrip(XpengTelematicsRow endRow) {
        BigDecimal endOdo = odometerLast;
        BigDecimal endSoc = socLast;
        LocalDateTime endedAt = lastDrivingAt;
        if (endRow != null) {
            if (endRow.odometerKm() != null) endOdo = endRow.odometerKm();
            if (endRow.socDisplay() != null) endSoc = endRow.socDisplay();
            endedAt = endRow.timer();
        }

        BigDecimal distance = (odometerStart != null && endOdo != null)
                ? endOdo.subtract(odometerStart).max(BigDecimal.ZERO)
                : BigDecimal.ZERO;

        if (distance.compareTo(MIN_DISTANCE_KM) < 0) {
            resetState();
            return null;
        }

        // Speed: null (statt 0) wenn der Parser esp_vehspd nicht aufloesen konnte -
        // sonst wuerden wir "kein Datum" mit "Auto stand still" verwechseln.
        BigDecimal avgSpeed = speedSamples > 0
                ? speedSum.divide(BigDecimal.valueOf(speedSamples), 2, RoundingMode.HALF_UP)
                : null;
        BigDecimal emittedMaxSpeed = speedSamples > 0 ? maxSpeed : null;

        // Discharge is conventionally positive current; positive battery current = drain.
        // We treat the absolute accumulated discharge as consumed energy.
        BigDecimal consumedKwh = energyAccumWh.abs()
                .divide(BigDecimal.valueOf(1000), 4, RoundingMode.HALF_UP);

        java.util.Map<String, Object> extras = extrasAggregator != null ? extrasAggregator.toMap() : null;
        DetectedTrip trip = new DetectedTrip(
                startedAt, endedAt,
                odometerStart, endOdo, distance,
                socStart, endSoc,
                consumedKwh.signum() == 0 ? null : consumedKwh,
                avgSpeed, emittedMaxSpeed,
                extras);
        resetState();
        return trip;
    }

    private void resetState() {
        startedAt = null;
        lastDrivingAt = null;
        odometerStart = null;
        odometerLast = null;
        socStart = null;
        socLast = null;
        maxSpeed = BigDecimal.ZERO;
        speedSum = BigDecimal.ZERO;
        speedSamples = 0;
        energyAccumWh = BigDecimal.ZERO;
        prevSampleTime = null;
        prevPowerW = null;
        extrasAggregator = null;
    }

    private static BigDecimal currentPowerW(XpengTelematicsRow row) {
        if (row.battVolt() == null || row.battCurrent() == null) return null;
        return row.battVolt().multiply(row.battCurrent(), MathContext.DECIMAL64);
    }

    private static BigDecimal nonNull(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
