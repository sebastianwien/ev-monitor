package com.evmonitor.application.routetype;

import com.evmonitor.domain.Car;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.EvTrip;
import com.evmonitor.domain.RouteType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reine Regeln zur Ableitung des Streckentyps einer Ladung. Einziger Ort für die Fahrt-Klassifikation
 * nach Tempo (ersetzt die alte SQL-Variante); ohne Spring, damit jede Schwelle einzeln testbar ist.
 *
 * Messbasis v2 (lokale DB, 20.10.2026, 18.864 Fahrten ab 10 km, Verbrauch je Fahrt normiert auf den
 * Median des Autos): Schnitt unter 60 km/h 0,97, 60-90 0,99, 90-100 1,04, ab 100 1,13; Spitze unter 100
 * 0,91, 120-140 1,00, 140-160 1,04, ab 160 1,08-1,17. Mehrverbrauch beginnt erst ab 90 km/h Schnitt bzw.
 * 140 km/h Spitze; Landstraße und 80er-Schnitt verbrauchen wie "gemischt". Darum HIGHWAY = Schnitt ab 90
 * oder Spitze ab 140 bei Schnitt ab 70 (Stau), CITY = Schnitt unter 50 und Spitze unter 80, Rest COMBINED.
 * Eine Ladung bekommt den Typ nur, wenn 75 % der km dazu passen: ein Viertel Fremd-km verwässert den
 * Effekt bereits von 13 auf 10 %, mehr macht die Statistik wertlos.
 * Faustregel ohne Fahrten (7.096 Ladungen): mittlere Leistung aus SoC-Hub zwischen zwei Ladungen ab 25 kW
 * trifft Autobahn zu 97 %, Stadt 0 %, gemischt 3 % (Recall 19 %). Stadt lässt sich aus dem Lademuster
 * nicht sicher erkennen, darum nur HIGHWAY per Faustregel.
 */
public final class RouteTypeRules {

    /** Ab diesem Durchschnittstempo gilt eine Fahrt als Autobahn. */
    static final double HIGHWAY_MIN_AVG_KMH = 90.0;
    /** Ab dieser Spitze gilt eine Fahrt auch bei langsamerem Schnitt (Stau, Pausen) als Autobahn ... */
    static final double HIGHWAY_MIN_MAX_KMH = 140.0;
    /** ... sofern der Schnitt mindestens so hoch ist. */
    static final double HIGHWAY_STAU_MIN_AVG_KMH = 70.0;
    /** Unter diesem Durchschnittstempo gilt eine Fahrt als Stadt ... */
    static final double CITY_MAX_AVG_KMH = 50.0;
    /** ... sofern die Spitze, falls bekannt, darunter bleibt. */
    static final double CITY_MAX_MAX_KMH = 80.0;
    /** Anteil der klassifizierten km, den ein Typ braucht, damit die Ladung ihn bekommt. */
    static final double MIN_TYPE_SHARE = 0.75;
    /** Kürzere Fahrten tragen kein Tempo-Signal (Rangieren, GPS-Rauschen). */
    static final double MIN_TRIP_KM = 0.5;
    /** Die Fahrten müssen mindestens diesen Anteil der Odometer-Differenz zwischen zwei Ladungen abdecken. */
    static final double MIN_TRIP_COVERAGE = 0.5;
    /** Ab dieser mittleren Entladeleistung zwischen zwei Ladungen gilt die Strecke als Autobahn. */
    static final BigDecimal HIGHWAY_MEAN_POWER_KW = new BigDecimal("25");
    /** Nur Lücken in diesem Bereich tragen ein Signal: kürzer ist Nachladen, länger verwischt Stillstand. */
    static final Duration MEAN_POWER_GAP_MIN = Duration.ofMinutes(30);
    static final Duration MEAN_POWER_GAP_MAX = Duration.ofHours(12);

    private RouteTypeRules() {}

    /** Ende einer Ladung: Beginn plus Dauer; ohne Dauer der Beginn. */
    public static OffsetDateTime chargeEndUtc(EvLog log) {
        int minutes = log.getChargeDurationMinutes() != null ? Math.max(0, log.getChargeDurationMinutes()) : 0;
        return log.getLoggedAt().plusMinutes(minutes).atOffset(java.time.ZoneOffset.UTC);
    }

    /** Fahrt nach Durchschnitts- und Höchsttempo klassifizieren; null ohne Durchschnitt. */
    public static RouteType classifyTrip(BigDecimal avgKmh, BigDecimal maxKmh) {
        if (avgKmh == null) return null;
        double avg = avgKmh.doubleValue();
        Double max = maxKmh != null ? maxKmh.doubleValue() : null;
        if (avg >= HIGHWAY_MIN_AVG_KMH) return RouteType.HIGHWAY;
        if (max != null && max >= HIGHWAY_MIN_MAX_KMH && avg >= HIGHWAY_STAU_MIN_AVG_KMH) return RouteType.HIGHWAY;
        if (avg < CITY_MAX_AVG_KMH && (max == null || max < CITY_MAX_MAX_KMH)) return RouteType.CITY;
        return RouteType.COMBINED;
    }

    /** Durchschnitt aus Strecke und Dauer; null, wenn Dauer oder Strecke kein Urteil zulassen. */
    public static RouteType classifyTrip(BigDecimal distanceKm, OffsetDateTime startedAt, OffsetDateTime endedAt, BigDecimal maxKmh) {
        if (distanceKm == null || startedAt == null || endedAt == null) return null;
        if (distanceKm.doubleValue() < MIN_TRIP_KM) return null;
        long seconds = Duration.between(startedAt, endedAt).getSeconds();
        if (seconds <= 0) return null;
        return classifyTrip(BigDecimal.valueOf(distanceKm.doubleValue() / (seconds / 3600.0)), maxKmh);
    }

    /** Tempo-Regel zuerst (eine Regel für alle Quellen), gespeicherter Typ nur ohne berechenbares Tempo. */
    static RouteType routeTypeOf(EvTrip trip) {
        RouteType bySpeed = trip.getAvgSpeedKmh() != null && trip.getDistanceKm() != null
                && trip.getDistanceKm().doubleValue() >= MIN_TRIP_KM
                ? classifyTrip(trip.getAvgSpeedKmh(), trip.getMaxSpeedKmh())
                : classifyTrip(trip.getDistanceKm(), trip.getTripStartedAt(), trip.getTripEndedAt(), trip.getMaxSpeedKmh());
        if (bySpeed != null) return bySpeed;
        if (trip.getRouteType() != null) {
            try {
                return RouteType.valueOf(trip.getRouteType());
            } catch (IllegalArgumentException ignored) {
                // unbekannter Wert: wie ohne Typ behandeln
            }
        }
        return null;
    }

    /** Typ mit mindestens {@link #MIN_TYPE_SHARE} der klassifizierten km, sonst COMBINED. Leer ohne klassifizierbare km. */
    public static Optional<RouteType> byKmShare(List<EvTrip> trips) {
        Map<RouteType, BigDecimal> km = new EnumMap<>(RouteType.class);
        for (EvTrip t : trips) {
            RouteType type = routeTypeOf(t);
            if (type == null || t.getDistanceKm() == null) continue;
            km.merge(type, t.getDistanceKm(), BigDecimal::add);
        }
        if (km.isEmpty()) return Optional.empty();
        BigDecimal total = km.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal required = total.multiply(BigDecimal.valueOf(MIN_TYPE_SHARE));
        if (km.getOrDefault(RouteType.HIGHWAY, BigDecimal.ZERO).compareTo(required) >= 0) return Optional.of(RouteType.HIGHWAY);
        if (km.getOrDefault(RouteType.CITY, BigDecimal.ZERO).compareTo(required) >= 0) return Optional.of(RouteType.CITY);
        return Optional.of(RouteType.COMBINED);
    }

    /**
     * Streckentyp aus den Fahrten zwischen Vorladung und aktueller Ladung. Beide Ladungen brauchen einen
     * Kilometerstand; die Fahrten müssen mindestens die Hälfte der gefahrenen km abdecken.
     */
    public static Optional<RouteType> fromTrips(EvLog previous, EvLog current, List<EvTrip> trips) {
        if (previous.getOdometerKm() == null || current.getOdometerKm() == null) return Optional.empty();
        int drivenKm = current.getOdometerKm() - previous.getOdometerKm();
        if (drivenKm <= 0) return Optional.empty();
        BigDecimal tripKm = trips.stream()
                .map(EvTrip::getDistanceKm)
                .filter(d -> d != null && d.doubleValue() >= MIN_TRIP_KM)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal required = BigDecimal.valueOf(drivenKm).multiply(BigDecimal.valueOf(MIN_TRIP_COVERAGE))
                .setScale(2, RoundingMode.HALF_UP);
        if (tripKm.compareTo(required) < 0) return Optional.empty();
        return byKmShare(trips);
    }

    /**
     * Mittlere Entladeleistung zwischen Ende der Vorladung und Beginn der aktuellen Ladung:
     * (SoC-Ende Vorladung minus SoC-Start aktuell) / 100 x effektive Kapazität (SoH-adjustiert) / Stunden.
     * Leer ohne beide SoC-Werte, ohne Kapazität, bei gestiegenem SoC oder Lücke außerhalb 0,5 bis 12 h.
     */
    public static Optional<BigDecimal> meanPowerKw(EvLog previous, EvLog current, Car car) {
        if (previous.getSocAfterChargePercent() == null || current.getSocBeforeChargePercent() == null) return Optional.empty();
        BigDecimal capacity = car.getEffectiveBatteryCapacityKwh();
        if (capacity == null || capacity.signum() <= 0) return Optional.empty();
        LocalDateTime gapStart = chargeEndUtc(previous).toLocalDateTime();
        Duration gap = Duration.between(gapStart, current.getLoggedAt());
        if (gap.compareTo(MEAN_POWER_GAP_MIN) < 0 || gap.compareTo(MEAN_POWER_GAP_MAX) > 0) return Optional.empty();
        BigDecimal socHub = previous.getSocAfterChargePercent().subtract(current.getSocBeforeChargePercent());
        if (socHub.signum() <= 0) return Optional.empty();
        BigDecimal kwh = socHub.multiply(capacity).divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP);
        BigDecimal hours = BigDecimal.valueOf(gap.getSeconds()).divide(new BigDecimal("3600"), 6, RoundingMode.HALF_UP);
        return Optional.of(kwh.divide(hours, 2, RoundingMode.HALF_UP));
    }

    /** Faustregel: ab {@link #HIGHWAY_MEAN_POWER_KW} Autobahn, sonst kein Urteil. */
    public static Optional<RouteType> fromMeanPower(EvLog previous, EvLog current, Car car) {
        return meanPowerKw(previous, current, car)
                .filter(kw -> kw.compareTo(HIGHWAY_MEAN_POWER_KW) >= 0)
                .map(kw -> RouteType.HIGHWAY);
    }
}
