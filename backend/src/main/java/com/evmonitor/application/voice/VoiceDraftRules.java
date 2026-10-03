package com.evmonitor.application.voice;

import com.evmonitor.domain.ChargingType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Nachregeln nach der Extraktion: deterministisch und darum im Code statt im Prompt. Die
 * Plausibilisierung markiert Werte in {@code uncertain}, verwirft sie aber nie - der Nutzer sieht
 * sie in der Pruefansicht und korrigiert selbst.
 */
final class VoiceDraftRules {

    static final Set<String> FIELDS = Set.of("placeIndex", "placeKind", "spokenOperator", "tariffIndex",
            "kwhCharged", "kwhAtVehicle", "socBefore", "socAfter", "odometerKm", "costEur", "pricePerKwh",
            "loggedAt", "chargeDurationMinutes", "maxChargingPowerKw", "chargingType", "routeType", "tireType");

    private static final double AC_MAX_KW = 22;
    /** Wie {@code ChargingSiteRef.name}: freier Modelltext wird nicht beliebig lang. */
    private static final int MAX_OPERATOR_LENGTH = 100;
    private static final DateTimeFormatter LOCAL_MINUTES = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final double MIN_PRICE_PER_KWH = 0.05;
    private static final double MAX_PRICE_PER_KWH = 1.50;

    private VoiceDraftRules() {}

    static DraftFields apply(DraftFields in, Integer lastOdometerKm) {
        Double energy = in.kwhCharged() != null ? in.kwhCharged() : in.kwhAtVehicle();
        // Das Modell setzt eine gesprochene kWh-Zahl gelegentlich zusaetzlich als Leistung
        Double peak = in.maxChargingPowerKw() != null && energy != null && Math.abs(in.maxChargingPowerKw() - energy) < 0.01
                ? null : in.maxChargingPowerKw();

        Set<String> uncertain = new LinkedHashSet<>();
        in.uncertain().stream().filter(FIELDS::contains).forEach(uncertain::add);
        if (in.odometerKm() != null && lastOdometerKm != null && in.odometerKm() < lastOdometerKm) uncertain.add("odometerKm");
        if (outOfPercent(in.socBefore())) uncertain.add("socBefore");
        if (outOfPercent(in.socAfter())) uncertain.add("socAfter");
        if (in.kwhAtVehicle() != null && in.kwhCharged() != null && in.kwhAtVehicle() > in.kwhCharged()) uncertain.add("kwhAtVehicle");
        String loggedAt = validLocalDateTime(in.loggedAt()) ? in.loggedAt() : null;
        if (in.loggedAt() != null && loggedAt == null) uncertain.add("loggedAt");
        if (in.pricePerKwh() != null && (in.pricePerKwh() < MIN_PRICE_PER_KWH || in.pricePerKwh() > MAX_PRICE_PER_KWH)) {
            uncertain.add("pricePerKwh");
        }

        return in.toBuilder()
                .maxChargingPowerKw(peak)
                .loggedAt(loggedAt)
                .spokenOperator(in.spokenOperator() == null || in.spokenOperator().length() <= MAX_OPERATOR_LENGTH
                        ? in.spokenOperator() : in.spokenOperator().substring(0, MAX_OPERATOR_LENGTH))
                .chargingType(chargingType(in.chargingType(), peak, energy, in.chargeDurationMinutes()))
                .costEur(in.costEur() != null ? in.costEur() : cost(in.kwhCharged(), in.pricePerKwh()))
                .uncertain(List.copyOf(uncertain))
                .build();
    }

    /**
     * Spiegelt {@code EvLog.inferChargingType}, liefert aber null statt UNKNOWN. Noetig, weil der
     * Wizard AC vorbelegt und das Backend den Typ sonst nie selbst ableitet.
     */
    private static ChargingType chargingType(ChargingType spoken, Double peakKw, Double energyKwh, Integer minutes) {
        if (spoken != null && spoken != ChargingType.UNKNOWN) return spoken;
        if (peakKw != null) return peakKw > AC_MAX_KW ? ChargingType.DC : ChargingType.AC;
        if (energyKwh != null && minutes != null && minutes >= 1) {
            return energyKwh / (minutes / 60.0) > AC_MAX_KW ? ChargingType.DC : ChargingType.AC;
        }
        return null;
    }

    /** costEur ist im Log Pflicht: wird nur ein Preis pro kWh genannt, ergibt er sich aus Preis mal kWh. */
    private static Double cost(Double kwhCharged, Double pricePerKwh) {
        if (kwhCharged == null || pricePerKwh == null) return null;
        return BigDecimal.valueOf(kwhCharged).multiply(BigDecimal.valueOf(pricePerKwh))
                .setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    /** Lokale Zeit "YYYY-MM-DDTHH:MM" wie im Schema verlangt, sonst wertlos fuer das Formular. */
    private static boolean validLocalDateTime(String v) {
        if (v == null) return false;
        try {
            LocalDateTime.parse(v, LOCAL_MINUTES);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private static boolean outOfPercent(Integer v) {
        return v != null && (v < 0 || v > 100);
    }
}
