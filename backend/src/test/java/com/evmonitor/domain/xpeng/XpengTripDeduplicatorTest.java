package com.evmonitor.domain.xpeng;

import com.evmonitor.domain.DataSource;
import com.evmonitor.domain.EvTrip;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dedup von XPeng-Trips ueber den echten Kilometerstand + enge Zeit-Toleranz,
 * unabhaengig von der exakt berechneten Startsekunde. Der bisherige externalId-
 * Dedup (VIN@startedAt) bleibt als Fast-Path bestehen; dieser Guard faengt
 * zusaetzlich Re-Imports ab, bei denen ein Detektor-Update die Startzeit um ein
 * paar Sekunden verschoben hat - sonst entstuenden Doppel-Trips.
 */
class XpengTripDeduplicatorTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 4, 22, 10, 0, 0);

    @Test
    void erkenntReImportTrotzLeichtVerschobenerStartzeit() {
        List<EvTrip> existing = List.of(
                xpengTrip("1000.0", T0)); // bereits importiert um 10:00:00
        // Re-Import desselben Trips, Start durch Detektor-Update 3s frueher.
        DetectedTrip reimported = trip("1000.0", T0.minusSeconds(3));
        assertTrue(XpengTripDeduplicator.isAlreadyImported(existing, reimported));
    }

    @Test
    void unterschiedlicherKilometerstandIstKeinDuplikat() {
        List<EvTrip> existing = List.of(xpengTrip("1000.0", T0));
        DetectedTrip other = trip("1005.0", T0.plusMinutes(2));
        assertFalse(XpengTripDeduplicator.isAlreadyImported(existing, other));
    }

    @Test
    void gleicherKmAberAusserhalbZeitToleranzIstKeinDuplikat() {
        // Zwei echte Kurztrips am selben (gerundeten) Kilometerstand, 10 min auseinander,
        // duerfen NICHT zusammengeworfen werden.
        List<EvTrip> existing = List.of(xpengTrip("1000.0", T0));
        DetectedTrip laterShortTrip = trip("1000.2", T0.plusMinutes(10));
        assertFalse(XpengTripDeduplicator.isAlreadyImported(existing, laterShortTrip));
    }

    @Test
    void ohneKilometerstandKeinGuard() {
        List<EvTrip> existing = List.of(xpengTrip("1000.0", T0));
        DetectedTrip noOdo = trip(null, T0);
        assertFalse(XpengTripDeduplicator.isAlreadyImported(existing, noOdo));
    }

    @Test
    void dedupNurGegenXpengImportQuelle() {
        // Ein zeitgleicher Trip aus einer anderen Quelle (z.B. Smartcar) ist kein
        // XPeng-Duplikat - nicht ueberspringen.
        List<EvTrip> existing = List.of(
                tripFromSource("1000.0", T0, DataSource.SMARTCAR_LIVE.name()));
        DetectedTrip xpeng = trip("1000.0", T0.plusSeconds(2));
        assertFalse(XpengTripDeduplicator.isAlreadyImported(existing, xpeng));
    }

    // -- helpers --

    private static DetectedTrip trip(String odoStart, LocalDateTime startedAt) {
        return new DetectedTrip(
                startedAt, startedAt.plusMinutes(15),
                odoStart == null ? null : new BigDecimal(odoStart),
                odoStart == null ? null : new BigDecimal(odoStart).add(new BigDecimal("5")),
                new BigDecimal("5"),
                new BigDecimal("80"), new BigDecimal("78"),
                new BigDecimal("1.0"),
                new BigDecimal("50"), new BigDecimal("60"),
                Map.of());
    }

    private static EvTrip xpengTrip(String odoStart, LocalDateTime startedAt) {
        return tripFromSource(odoStart, startedAt, DataSource.XPENG_IMPORT.name());
    }

    private static EvTrip tripFromSource(String odoStart, LocalDateTime startedAt, String source) {
        return EvTrip.builder()
                .dataSource(source)
                .tripStartedAt(startedAt.atOffset(ZoneOffset.UTC))
                .odometerStartKm(new BigDecimal(odoStart))
                .build();
    }
}
