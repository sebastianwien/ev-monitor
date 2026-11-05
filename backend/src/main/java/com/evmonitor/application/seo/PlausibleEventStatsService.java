package com.evmonitor.application.seo;

import com.evmonitor.infrastructure.external.plausible.PlausibleClient;
import com.evmonitor.infrastructure.external.plausible.PlausibleClient.Query;
import com.evmonitor.infrastructure.external.plausible.PlausibleClient.Row;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Admin-Reiter "Events": welche Events in den letzten 30 Tagen ankamen und welche Namen in Plausible
 * als Goal angelegt sind. Plausible liefert unter {@code event:goal} nur angelegte Goals, auch
 * Seitenaufruf-Goals ohne Event. Live abgefragt, eine Stunde im Speicher; nichts in der DB.
 */
@Service
@RequiredArgsConstructor
public class PlausibleEventStatsService {

    private static final long TTL_MS = 3_600_000;
    private static final int DAYS = 30;

    private final PlausibleClient client;

    private volatile EventStats cached;
    private volatile long cachedAt;

    public record EventStat(String name, long visitors, long events, boolean goal) {
    }

    public record EventStats(boolean enabled, LocalDate from, LocalDate to, List<EventStat> events) {
    }

    public EventStats last30Days() {
        EventStats c = cached;
        if (c != null && System.currentTimeMillis() - cachedAt < TTL_MS) return c;
        EventStats fresh = load();
        // Ohne Key nicht puffern: sobald er gesetzt ist, sollen die Zahlen sofort kommen.
        if (fresh.enabled()) {
            cached = fresh;
            cachedAt = System.currentTimeMillis();
        }
        return fresh;
    }

    void clearCache() {
        cached = null;
    }

    private EventStats load() {
        LocalDate to = LocalDate.now().minusDays(1);
        LocalDate from = to.minusDays(DAYS - 1L);
        if (!client.isEnabled()) return new EventStats(false, from, to, List.of());

        List<String> metrics = List.of("visitors", "events");
        List<Row> names = client.query(new Query(metrics, from, to, List.of("event:name"), null));
        List<Row> goals = client.query(new Query(metrics, from, to, List.of("event:goal"), null));
        Set<String> goalNames = goals.stream().map(r -> r.dim(0)).collect(Collectors.toSet());

        Map<String, EventStat> byName = new LinkedHashMap<>();
        for (Row r : names) {
            String name = r.dim(0);
            if ("pageview".equals(name)) continue;
            byName.put(name, new EventStat(name, (long) r.metric(0), (long) r.metric(1), goalNames.contains(name)));
        }
        for (Row r : goals) {
            byName.putIfAbsent(r.dim(0), new EventStat(r.dim(0), (long) r.metric(0), (long) r.metric(1), true));
        }
        return new EventStats(true, from, to, new ArrayList<>(byName.values()));
    }
}
