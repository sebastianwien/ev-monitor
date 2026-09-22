package com.evmonitor.application;

import com.evmonitor.domain.DataSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Fasst Ladevorgaenge fuer die oeffentliche Fahrzeugseite zu Ladegruppen zusammen,
 * damit Ueberschussladen die Liste nicht mit Micro-Vorgaengen fuellt.
 *
 * <p>Dieselben zwei Regeln wie das Dashboard in {@code useLogList.ts} (mergedLogFeed),
 * die beiden muessen zusammen geaendert werden:
 * <ol>
 *   <li>Wallbox- und Upload-Vorgaenge ohne Odometer am selben Kalendertag</li>
 *   <li>danach zeitlich aufeinanderfolgende Vorgaenge mit identischem Odometer,
 *       ein Vorgang ohne Odometer unterbricht die Kette</li>
 * </ol>
 * Gruppiert wird serverseitig, damit Odometer und Datenquelle die oeffentliche
 * Antwort nicht verlassen.
 */
final class PublicChargeGrouper {

    private PublicChargeGrouper() {}

    /** @param logs neueste zuerst, wie {@code EvLogService.getLogsForCar} sie liefert */
    static List<PublicCarResponse.Charge> group(List<EvLogResponse> logs, int limit) {
        List<EvLogResponse> newestFirst = logs.stream()
                .sorted(Comparator.comparing(EvLogResponse::loggedAt).reversed())
                .toList();

        // Regel 1: pro Tag, nur wenn mindestens zwei zusammenkommen.
        Map<LocalDate, List<EvLogResponse>> byDay = new LinkedHashMap<>();
        for (EvLogResponse l : newestFirst) {
            if (l.odometerKm() == null && isSurplusSource(l.dataSource())) {
                byDay.computeIfAbsent(l.loggedAt().toLocalDate(), d -> new ArrayList<>()).add(l);
            }
        }
        List<List<EvLogResponse>> groups = new ArrayList<>();
        List<EvLogResponse> rest = new ArrayList<>();
        for (EvLogResponse l : newestFirst) {
            List<EvLogResponse> day = l.odometerKm() == null && isSurplusSource(l.dataSource())
                    ? byDay.get(l.loggedAt().toLocalDate()) : null;
            if (day != null && day.size() >= 2) {
                if (day.get(0) == l) groups.add(day);
            } else {
                rest.add(l);
            }
        }

        // Regel 2: gleicher Odometer in Folge.
        int i = 0;
        while (i < rest.size()) {
            Integer odo = rest.get(i).odometerKm();
            int j = i + 1;
            while (odo != null && j < rest.size() && Objects.equals(rest.get(j).odometerKm(), odo)) j++;
            groups.add(rest.subList(i, j));
            i = j;
        }

        return groups.stream()
                .sorted(Comparator.comparing((List<EvLogResponse> g) -> g.get(0).loggedAt()).reversed())
                .limit(limit)
                .map(PublicChargeGrouper::toCharge)
                .toList();
    }

    private static boolean isSurplusSource(DataSource source) {
        return source == DataSource.WALLBOX_GOE || source == DataSource.API_UPLOAD;
    }

    /** @param group neueste zuerst, mindestens ein Eintrag */
    private static PublicCarResponse.Charge toCharge(List<EvLogResponse> group) {
        EvLogResponse newest = group.get(0);
        EvLogResponse oldest = group.get(group.size() - 1);
        BigDecimal kwh = null, cost = null, peak = null;
        Integer minutes = null;
        boolean anyPublic = false;
        String type = null;
        for (EvLogResponse l : group) {
            kwh = add(kwh, l.kwhCharged());
            cost = add(cost, l.costEur());
            if (l.chargeDurationMinutes() != null) minutes = (minutes == null ? 0 : minutes) + l.chargeDurationMinutes();
            if (l.maxChargingPowerKw() != null) peak = peak == null ? l.maxChargingPowerKw() : peak.max(l.maxChargingPowerKw());
            anyPublic |= Boolean.TRUE.equals(l.isPublicCharging());
            if (l.chargingType() != null && (type == null || "DC".equals(l.chargingType().name()))) type = l.chargingType().name();
        }
        // Verbrauch: nur der aelteste Vorgang der Gruppe hat den gefahrenen Abschnitt davor.
        BigDecimal consumption = Boolean.TRUE.equals(oldest.consumptionImplausible()) ? null : oldest.consumptionKwhPer100km();
        return new PublicCarResponse.Charge(
                newest.loggedAt().toLocalDate(), kwh, cost, minutes, type, peak, consumption, anyPublic, group.size());
    }

    private static BigDecimal add(BigDecimal sum, BigDecimal v) {
        if (v == null) return sum;
        return sum == null ? v : sum.add(v);
    }
}
