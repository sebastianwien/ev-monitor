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

    // Plausibilitaetsgrenzen fuer die DC-Pack-Integration aus bms_battvolt x bms_battcurr.
    // Reale HV-Packs liegen zwischen ~250 V (entladen) und ~480 V (Tesla Plaid),
    // Stromspitzen beim DC-Schnellladen bis ~600 A. XPeng-Sensoren liefern
    // gelegentlich Wake-up-Sentinels (zehntausende Ampere), die wir hier kappen.
    private static final BigDecimal MIN_BATT_VOLT_V = new BigDecimal("100");
    private static final BigDecimal MAX_BATT_VOLT_V = new BigDecimal("800");
    private static final BigDecimal MAX_BATT_CURRENT_A = new BigDecimal("1000");
    private static final BigDecimal MAX_PACK_POWER_KW = new BigDecimal("400");

    // Plausibilitaetsfenster fuer die Netto-Energie relativ zur Brutto-Energie.
    // OBC-Wirkungsgrad real: AC ~88-95 %, DC ~95-99 %. Werte unter 70 % oder
    // ueber 105 % sind Sensor-Defekt/Vorzeichen-Bug - in diesem Fall null
    // zurueckgeben und Konsumenten auf SoC-Delta-Fallback umleiten.
    private static final BigDecimal MIN_NET_GROSS_RATIO = new BigDecimal("0.70");
    private static final BigDecimal MAX_NET_GROSS_RATIO = new BigDecimal("1.05");

    private State state = State.IDLE;
    private LocalDateTime startedAt;
    private LocalDateTime lastEnergyAt;
    private LocalDateTime lastPositivePowerAt;
    private BigDecimal socStart;
    private BigDecimal socLast;
    private BigDecimal odometerLast;
    private BigDecimal maxPowerKw = BigDecimal.ZERO;
    private BigDecimal energyAccumKwh = BigDecimal.ZERO;
    private BigDecimal energyAccumPackKwh = BigDecimal.ZERO;
    private boolean hasValidPackSample = false;
    private ChargingExtrasAggregator extrasAggregator;
    private LocalDateTime prevSampleTime;
    private BigDecimal prevPowerKw;
    private BigDecimal prevBattVolt;
    private BigDecimal prevBattCurrent;

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
            // still within grace - track sample for integration boundary but treat power as 0.
            // prevBatt* aktuell halten, damit die naechste Aufnahme nach der Pause
            // saubere Trapez-Endpunkte hat (alte Werte waeren stale).
            prevSampleTime = row.timer();
            prevPowerKw = BigDecimal.ZERO;
            prevBattVolt = row.battVolt();
            prevBattCurrent = row.battCurrent();
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
        energyAccumPackKwh = BigDecimal.ZERO;
        hasValidPackSample = false;
        prevSampleTime = row.timer();
        prevPowerKw = row.chargePowerKw();
        prevBattVolt = row.battVolt();
        prevBattCurrent = row.battCurrent();
        lastEnergyAt = row.timer();
        lastPositivePowerAt = row.timer();
        extrasAggregator = new ChargingExtrasAggregator();
        extrasAggregator.consume(row);
    }

    private void accumulate(XpengTelematicsRow row) {
        if (row.socDisplay() != null) socLast = row.socDisplay();
        if (row.odometerKm() != null) odometerLast = row.odometerKm();
        if (row.chargePowerKw().compareTo(maxPowerKw) > 0) maxPowerKw = row.chargePowerKw();
        if (extrasAggregator != null) extrasAggregator.consume(row);

        if (prevSampleTime != null && prevPowerKw != null) {
            Duration dt = Duration.between(prevSampleTime, row.timer());
            if (!dt.isNegative() && dt.compareTo(MAX_INTEGRATION_GAP) <= 0) {
                BigDecimal dtHours = BigDecimal.valueOf(dt.toMillis())
                        .divide(BigDecimal.valueOf(3_600_000), MathContext.DECIMAL64);
                BigDecimal avgKw = prevPowerKw.add(row.chargePowerKw())
                        .divide(BigDecimal.valueOf(2), MathContext.DECIMAL64);
                energyAccumKwh = energyAccumKwh.add(avgKw.multiply(dtHours, MathContext.DECIMAL64));
                accumulatePackEnergy(row, dtHours);
                lastEnergyAt = row.timer();
            }
        }
        prevSampleTime = row.timer();
        prevPowerKw = row.chargePowerKw();
        prevBattVolt = row.battVolt();
        prevBattCurrent = row.battCurrent();
    }

    /**
     * Integriert die DC-seitige Pack-Leistung aus U x I parallel zur AC-Eingangsleistung.
     * Trapezregel ueber das aktuelle und das vorherige Sample. Implausible Samples
     * (Sensor-Wake-up-Sentinels, fehlende Werte) werden uebersprungen - das verzerrt
     * die Gesamtsumme nur minimal, da der naechste valide Sample-Paar bereits 5 s
     * spaeter wieder weitermacht.
     *
     * <p>Vorzeichen: bei XPeng ist beim Laden {@code battCurrent < 0}. Wir negieren
     * deshalb {@code U x I}, damit positive Werte "Energie in den Pack" bedeuten.
     */
    private void accumulatePackEnergy(XpengTelematicsRow row, BigDecimal dtHours) {
        if (!isPlausibleBattSample(prevBattVolt, prevBattCurrent)
                || !isPlausibleBattSample(row.battVolt(), row.battCurrent())) {
            return;
        }
        BigDecimal prevPackKw = packPowerKw(prevBattVolt, prevBattCurrent);
        BigDecimal currPackKw = packPowerKw(row.battVolt(), row.battCurrent());
        if (prevPackKw.abs().compareTo(MAX_PACK_POWER_KW) > 0
                || currPackKw.abs().compareTo(MAX_PACK_POWER_KW) > 0) {
            return;
        }
        BigDecimal avgPackKw = prevPackKw.add(currPackKw).divide(BigDecimal.valueOf(2), MathContext.DECIMAL64);
        energyAccumPackKwh = energyAccumPackKwh.add(avgPackKw.multiply(dtHours, MathContext.DECIMAL64));
        hasValidPackSample = true;
    }

    private static boolean isPlausibleBattSample(BigDecimal volt, BigDecimal current) {
        return volt != null
                && current != null
                && volt.compareTo(MIN_BATT_VOLT_V) >= 0
                && volt.compareTo(MAX_BATT_VOLT_V) <= 0
                && current.abs().compareTo(MAX_BATT_CURRENT_A) <= 0;
    }

    /**
     * Wandelt {@code bms_battvolt} (V) und {@code bms_battcurr} (A) in DC-seitige
     * Pack-Leistung (kW). Vorzeichen wird negiert, weil XPeng beim Laden negative
     * Stroeme meldet (Konvention empirisch aus Igor-G9-Daten 2026-05-16
     * verifiziert; DATA_CATALOGUE spezifiziert die Richtung nicht).
     * Positive Rueckgabe = Energie fliesst in den Pack.
     */
    private static BigDecimal packPowerKw(BigDecimal volt, BigDecimal current) {
        return volt.multiply(current, MathContext.DECIMAL64)
                .negate()
                .divide(BigDecimal.valueOf(1000), MathContext.DECIMAL64);
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

        java.util.Map<String, Object> extras = extrasAggregator != null ? extrasAggregator.toMap() : null;
        DetectedChargingSession session = new DetectedChargingSession(
                startedAt, endedAt,
                socStart, socLast,
                kwh,
                packKwhAtVehicle(kwh),
                maxPowerKw.setScale(2, RoundingMode.HALF_UP),
                odometerLast,
                DetectedChargingSession.classifyChargingType(maxPowerKw),
                extras);
        resetState();
        return session;
    }

    /**
     * Liefert die in den Pack geflossene Netto-Energie, oder {@code null} wenn die
     * Integration nicht vertrauenswuerdig ist:
     * <ul>
     *   <li>keine validen U/I-Samples in der Session gesehen wurden,
     *   <li>die Brutto-Referenz fehlt oder nicht-positiv ist,
     *   <li>das Netto/Brutto-Verhaeltnis ausserhalb [70 %, 105 %] liegt - das
     *       kennzeichnet Sensor-Defekt oder invertiertes Vorzeichen.
     * </ul>
     * In allen Faellen muss der Konsument auf SoC-Delta-Schaetzung zurueckfallen.
     *
     * @param grossKwh die parallel integrierte Brutto-Energie aus {@code chrgpwr}
     */
    private BigDecimal packKwhAtVehicle(BigDecimal grossKwh) {
        if (!hasValidPackSample) return null;
        if (grossKwh == null || grossKwh.signum() <= 0) return null;
        if (energyAccumPackKwh.signum() <= 0) return null;
        BigDecimal ratio = energyAccumPackKwh.divide(grossKwh, 4, RoundingMode.HALF_UP);
        if (ratio.compareTo(MIN_NET_GROSS_RATIO) < 0 || ratio.compareTo(MAX_NET_GROSS_RATIO) > 0) {
            return null;
        }
        return energyAccumPackKwh.setScale(3, RoundingMode.HALF_UP);
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
        energyAccumPackKwh = BigDecimal.ZERO;
        hasValidPackSample = false;
        prevSampleTime = null;
        prevPowerKw = null;
        prevBattVolt = null;
        prevBattCurrent = null;
        extrasAggregator = null;
    }
}
