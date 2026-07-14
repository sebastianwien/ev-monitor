package com.evmonitor.application.imports.eudataact;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/** Nach Feldnamen gruppierte, chronologisch sortierte Rohwerte. */
record EntryIndex(Map<String, List<DataEntry>> byField) {

    static EntryIndex of(List<DataEntry> entries) {
        Map<String, List<DataEntry>> grouped = entries.stream()
                .collect(Collectors.groupingBy(DataEntry::field));
        grouped.values().forEach(list -> list.sort(Comparator.comparing(DataEntry::timestamp)));
        return new EntryIndex(Map.copyOf(grouped));
    }

    List<DataEntry> get(String field) {
        return byField.getOrDefault(field, List.of());
    }

    boolean has(String field) {
        return !get(field).isEmpty();
    }

    Set<String> fields() {
        return byField.keySet();
    }

    /** Alle Werte eines Feldes im geschlossenen Intervall [from, to]. */
    List<DataEntry> between(String field, OffsetDateTime from, OffsetDateTime to) {
        return get(field).stream()
                .filter(e -> !e.timestamp().isBefore(from) && !e.timestamp().isAfter(to))
                .toList();
    }

    /** Letzter Wert eines Feldes zum Zeitpunkt t (oder davor). */
    Optional<DataEntry> lastAt(String field, OffsetDateTime t) {
        List<DataEntry> list = get(field);
        DataEntry found = null;
        for (DataEntry e : list) {
            if (e.timestamp().isAfter(t)) break;
            found = e;
        }
        return Optional.ofNullable(found);
    }
}
