package com.evmonitor.application.routetype;

import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.EvTrip;
import com.evmonitor.domain.RouteType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reine Regeln zur Ableitung des Streckentyps einer Ladung. Einziger Ort für die Fahrt-Klassifikation
 * nach Tempo (ersetzt die alte SQL-Variante); ohne Spring, damit jede Schwelle einzeln testbar ist.
 *
 * Messbasis (lokale DB, Referenz Tessie-Klassifikation, 11.10.2026): Tempo = Strecke / Dauer mit
 * Schwellen 80 / 50 km/h klassifiziert 86 % der km gleich, erkennt Autobahn zu 92 % bei 12 % Fehlalarm.
 */
public final class RouteTypeRules {

    /** Ab diesem Durchschnittstempo gilt eine Fahrt als Autobahn. */
    static final double HIGHWAY_MIN_SPEED_KMH = 80.0;
    /** Unter diesem Durchschnittstempo gilt eine Fahrt als Stadt. */
    static final double CITY_MAX_SPEED_KMH = 50.0;
    /** Kürzere Fahrten tragen kein Tempo-Signal (Rangieren, GPS-Rauschen). */
    static final double MIN_TRIP_KM = 0.5;
    /** Die Fahrten müssen mindestens diesen Anteil der Odometer-Differenz zwischen zwei Ladungen abdecken. */
    static final double MIN_TRIP_COVERAGE = 0.5;

    private RouteTypeRules() {}

    /** Ende einer Ladung: Beginn plus Dauer; ohne Dauer der Beginn. */
    public static OffsetDateTime chargeEndUtc(EvLog log) {
        int minutes = log.getChargeDurationMinutes() != null ? Math.max(0, log.getChargeDurationMinutes()) : 0;
        return log.getLoggedAt().plusMinutes(minutes).atOffset(java.time.ZoneOffset.UTC);
    }

    /** Fahrt nach Durchschnittstempo klassifizieren; null, wenn Dauer oder Strecke kein Urteil zulassen. */
    public static RouteType classifyTrip(BigDecimal distanceKm, OffsetDateTime startedAt, OffsetDateTime endedAt) {
        if (distanceKm == null || startedAt == null || endedAt == null) return null;
        if (distanceKm.doubleValue() < MIN_TRIP_KM) return null;
        long seconds = Duration.between(startedAt, endedAt).getSeconds();
        if (seconds <= 0) return null;
        double kmh = distanceKm.doubleValue() / (seconds / 3600.0);
        if (kmh >= HIGHWAY_MIN_SPEED_KMH) return RouteType.HIGHWAY;
        if (kmh < CITY_MAX_SPEED_KMH) return RouteType.CITY;
        return RouteType.COMBINED;
    }

    /** Gespeicherter Typ der Fahrt, sonst Tempo-Klassifikation. */
    static RouteType routeTypeOf(EvTrip trip) {
        if (trip.getRouteType() != null) {
            try {
                return RouteType.valueOf(trip.getRouteType());
            } catch (IllegalArgumentException ignored) {
                // unbekannter Wert: wie ohne Typ behandeln
            }
        }
        return classifyTrip(trip.getDistanceKm(), trip.getTripStartedAt(), trip.getTripEndedAt());
    }

    /** Typ mit den meisten km; nur bei strikter Mehrheit, sonst COMBINED. Leer ohne klassifizierbare km. */
    public static Optional<RouteType> dominantByKm(List<EvTrip> trips) {
        Map<RouteType, BigDecimal> km = new EnumMap<>(RouteType.class);
        for (EvTrip t : trips) {
            RouteType type = routeTypeOf(t);
            if (type == null || t.getDistanceKm() == null) continue;
            km.merge(type, t.getDistanceKm(), BigDecimal::add);
        }
        if (km.isEmpty()) return Optional.empty();
        BigDecimal highway = km.getOrDefault(RouteType.HIGHWAY, BigDecimal.ZERO);
        BigDecimal city = km.getOrDefault(RouteType.CITY, BigDecimal.ZERO);
        BigDecimal combined = km.getOrDefault(RouteType.COMBINED, BigDecimal.ZERO);
        if (highway.compareTo(combined) > 0 && highway.compareTo(city) > 0) return Optional.of(RouteType.HIGHWAY);
        if (city.compareTo(combined) > 0 && city.compareTo(highway) > 0) return Optional.of(RouteType.CITY);
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
        return dominantByKm(trips);
    }
}
