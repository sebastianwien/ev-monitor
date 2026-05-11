package com.evmonitor.domain.xpeng;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Stateful charging-session detector for the XPeng 5s-telematics stream.
 *
 * Session semantics:
 *   - starts when chargePowerKw > 0 AND gear == P
 *   - tolerates brief power drops (e.g. CP renegotiation) up to ZERO_POWER_GRACE
 *   - ends when chargePower stays 0 longer than the grace, gear leaves P, or stream ends
 */
public class XpengChargeDetector {

    private static final Duration ZERO_POWER_GRACE = Duration.ofSeconds(60);
    private static final Duration MAX_INTEGRATION_GAP = Duration.ofSeconds(60);
    private static final BigDecimal MIN_SESSION_KWH = new BigDecimal("0.05");

    private State state = State.IDLE;
    private LocalDateTime startedAt;
    private LocalDateTime lastEnergyAt;
    private LocalDateTime lastPositivePowerAt;
    private BigDecimal socStart;
    private BigDecimal socLast;
    private BigDecimal odometerLast;
    private BigDecimal maxPowerKw = BigDecimal.ZERO;
    private BigDecimal energyAccumKwh = BigDecimal.ZERO;
    private LocalDateTime prevSampleTime;
    private BigDecimal prevPowerKw;

    private enum State { IDLE, CHARGING }

    public Optional<DetectedChargingSession> consume(XpengTelematicsRow row) {
        if (row == null || row.timer() == null) return Optional.empty();

        return switch (state) {
            case IDLE -> handleIdle(row);
            case CHARGING -> handleCharging(row);
        };
    }

    private Optional<DetectedChargingSession> handleIdle(XpengTelematicsRow row) {
        if (row.isCharging() && row.isParked()) {
            startSession(row);
        }
        return Optional.empty();
    }

    private Optional<DetectedChargingSession> handleCharging(XpengTelematicsRow row) {
        // Gear left P → end the session.
        if (row.gearLev() != null && !row.isParked()) {
            DetectedChargingSession emitted = finalizeSession(row);
            state = State.IDLE;
            return Optional.ofNullable(emitted);
        }

        if (row.isCharging()) {
            accumulate(row);
            lastPositivePowerAt = row.timer();
        } else {
            // No power. If the grace period elapsed since last positive power, close session.
            if (lastPositivePowerAt != null
                    && Duration.between(lastPositivePowerAt, row.timer()).compareTo(ZERO_POWER_GRACE) > 0) {
                DetectedChargingSession emitted = finalizeSession(row);
                state = State.IDLE;
                return Optional.ofNullable(emitted);
            }
            // still within grace - track sample for integration boundary but treat power as 0
            prevSampleTime = row.timer();
            prevPowerKw = BigDecimal.ZERO;
        }
        return Optional.empty();
    }

    public Optional<DetectedChargingSession> finish() {
        if (state == State.CHARGING) {
            DetectedChargingSession emitted = finalizeSession(null);
            state = State.IDLE;
            return Optional.ofNullable(emitted);
        }
        return Optional.empty();
    }

    private void startSession(XpengTelematicsRow row) {
        state = State.CHARGING;
        startedAt = row.timer();
        socStart = row.socDisplay();
        socLast = row.socDisplay();
        odometerLast = row.odometerKm();
        maxPowerKw = row.chargePowerKw();
        energyAccumKwh = BigDecimal.ZERO;
        prevSampleTime = row.timer();
        prevPowerKw = row.chargePowerKw();
        lastEnergyAt = row.timer();
        lastPositivePowerAt = row.timer();
    }

    private void accumulate(XpengTelematicsRow row) {
        if (row.socDisplay() != null) socLast = row.socDisplay();
        if (row.odometerKm() != null) odometerLast = row.odometerKm();
        if (row.chargePowerKw().compareTo(maxPowerKw) > 0) maxPowerKw = row.chargePowerKw();

        if (prevSampleTime != null && prevPowerKw != null) {
            Duration dt = Duration.between(prevSampleTime, row.timer());
            if (!dt.isNegative() && dt.compareTo(MAX_INTEGRATION_GAP) <= 0) {
                BigDecimal avgKw = prevPowerKw.add(row.chargePowerKw()).divide(BigDecimal.valueOf(2), MathContext.DECIMAL64);
                BigDecimal dtHours = BigDecimal.valueOf(dt.toMillis()).divide(BigDecimal.valueOf(3_600_000), MathContext.DECIMAL64);
                energyAccumKwh = energyAccumKwh.add(avgKw.multiply(dtHours, MathContext.DECIMAL64));
                lastEnergyAt = row.timer();
            }
        }
        prevSampleTime = row.timer();
        prevPowerKw = row.chargePowerKw();
    }

    private DetectedChargingSession finalizeSession(XpengTelematicsRow endRow) {
        LocalDateTime endedAt = lastPositivePowerAt != null ? lastPositivePowerAt : startedAt;
        if (endRow != null && endRow.timer() != null) {
            // Cap the end timestamp at the last actual positive power sample - we don't want
            // to attribute the zero-power tail to the session duration.
            endedAt = lastPositivePowerAt != null ? lastPositivePowerAt : endRow.timer();
            if (endRow.socDisplay() != null) socLast = endRow.socDisplay();
            if (endRow.odometerKm() != null) odometerLast = endRow.odometerKm();
        }

        BigDecimal kwh = energyAccumKwh.setScale(3, RoundingMode.HALF_UP);
        if (kwh.compareTo(MIN_SESSION_KWH) < 0) {
            resetState();
            return null;
        }

        DetectedChargingSession session = new DetectedChargingSession(
                startedAt, endedAt,
                socStart, socLast,
                kwh,
                maxPowerKw.setScale(2, RoundingMode.HALF_UP),
                odometerLast,
                DetectedChargingSession.classifyChargingType(maxPowerKw));
        resetState();
        return session;
    }

    private void resetState() {
        startedAt = null;
        lastEnergyAt = null;
        lastPositivePowerAt = null;
        socStart = null;
        socLast = null;
        odometerLast = null;
        maxPowerKw = BigDecimal.ZERO;
        energyAccumKwh = BigDecimal.ZERO;
        prevSampleTime = null;
        prevPowerKw = null;
    }
}
