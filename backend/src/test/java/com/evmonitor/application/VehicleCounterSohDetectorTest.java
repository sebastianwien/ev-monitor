package com.evmonitor.application;

import com.evmonitor.application.VehicleCounterSohDetector.CounterTrip;
import com.evmonitor.application.VehicleCounterSohDetector.Detection;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class VehicleCounterSohDetectorTest {

    private static final Instant T0 = Instant.parse("2026-10-01T06:00:00Z");

    /** n Fahrten mit je {@code hub} Prozentpunkten und {@code kwhPerPercent} kWh je Prozent, eine je Tag ab {@code dayOffset}. */
    private static List<CounterTrip> trips(int n, int hub, double kwhPerPercent, int dayOffset) {
        List<CounterTrip> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(new CounterTrip(T0.plus(Duration.ofDays(dayOffset + i)), BigDecimal.valueOf(90), BigDecimal.valueOf(90 - hub),
                    BigDecimal.valueOf(hub * kwhPerPercent)));
        }
        return out;
    }

    private static List<CounterTrip> trips(int n, int hub, double kwhPerPercent) {
        return trips(n, hub, kwhPerPercent, 0);
    }

    @Test
    void medianOfFiveLongTrips() {
        // 5 Fahrten à 40 %, 1,04 kWh je Prozent -> 104 kWh nutzbar
        Detection d = VehicleCounterSohDetector.detect(trips(5, 40, 1.04), List.of()).orElseThrow();
        assertEquals(new BigDecimal("104.00"), d.capacityKwh());
        assertEquals(5, d.tripCount());
        assertEquals(new BigDecimal("40"), d.socHubPercent());
    }

    @Test
    void needsFiveLongTrips() {
        assertTrue(VehicleCounterSohDetector.detect(trips(4, 60, 1.04), List.of()).isEmpty());
    }

    @Test
    void shortTripsDoNotLowerTheCapacity() {
        // Regression: SoC in ganzen Prozent. Kurzfahrten mit 0,5 kWh zeigen mal 1, mal 0 Prozentpunkte.
        // Wer nur die mit 1 Prozentpunkt mitzählt, rechnet 50 kWh je 100 % und drückt den SoH.
        List<CounterTrip> list = new ArrayList<>(trips(5, 40, 1.04));
        for (int i = 0; i < 30; i++) {
            list.add(new CounterTrip(T0.plus(Duration.ofDays(10 + i)), BigDecimal.valueOf(70), BigDecimal.valueOf(69), new BigDecimal("0.5")));
        }
        Detection d = VehicleCounterSohDetector.detect(list, List.of()).orElseThrow();
        assertEquals(new BigDecimal("104.00"), d.capacityKwh());
        assertEquals(5, d.tripCount());
    }

    @Test
    void tripCountsFromThirtyPercentHub() {
        assertTrue(VehicleCounterSohDetector.detect(trips(5, 29, 1.04), List.of()).isEmpty(), "29 %");
        assertTrue(VehicleCounterSohDetector.detect(trips(5, 30, 1.04), List.of()).isPresent(), "30 %");
    }

    @Test
    void usesTheLatestFiveTrips() {
        List<CounterTrip> list = new ArrayList<>(trips(5, 40, 1.00, 20)); // neuere Fahrten zuerst in der Liste
        list.addAll(trips(5, 40, 1.07, 0));
        assertEquals(new BigDecimal("100.00"), VehicleCounterSohDetector.detect(list, List.of()).orElseThrow().capacityKwh());
    }

    @Test
    void medianIgnoresASingleOutlier() {
        List<CounterTrip> list = new ArrayList<>(trips(4, 40, 1.04));
        list.add(new CounterTrip(T0.plus(Duration.ofDays(9)), BigDecimal.valueOf(90), BigDecimal.valueOf(50), new BigDecimal("28.0")));
        assertEquals(new BigDecimal("104.00"), VehicleCounterSohDetector.detect(list, List.of()).orElseThrow().capacityKwh());
    }

    @Test
    void ignoresTripsWithoutSocDropOrWithoutCounter() {
        List<CounterTrip> list = new ArrayList<>(trips(5, 40, 1.04));
        list.add(new CounterTrip(T0.plus(Duration.ofDays(30)), BigDecimal.valueOf(68), BigDecimal.valueOf(68), BigDecimal.valueOf(0.4)));
        list.add(new CounterTrip(T0.plus(Duration.ofDays(31)), BigDecimal.valueOf(90), BigDecimal.valueOf(50), null));
        list.add(new CounterTrip(T0.plus(Duration.ofDays(32)), null, BigDecimal.valueOf(50), BigDecimal.valueOf(40)));
        list.add(new CounterTrip(null, BigDecimal.valueOf(90), BigDecimal.valueOf(50), BigDecimal.valueOf(40)));
        Detection d = VehicleCounterSohDetector.detect(list, List.of()).orElseThrow();
        assertEquals(5, d.tripCount());
        assertEquals(new BigDecimal("104.00"), d.capacityKwh());
    }

    @Test
    void excludesTripsStartingWithinAnHourAfterDcCharge() {
        List<CounterTrip> base = trips(4, 40, 1.04);
        Instant dcEnd = T0.plus(Duration.ofDays(40));
        CounterTrip soon = new CounterTrip(dcEnd.plus(Duration.ofMinutes(20)), BigDecimal.valueOf(90), BigDecimal.valueOf(50), BigDecimal.valueOf(41.6));
        List<CounterTrip> list = new ArrayList<>(base);
        list.add(soon);
        assertTrue(VehicleCounterSohDetector.detect(list, List.of(dcEnd)).isEmpty(), "Fahrt 20 min nach DC zählt nicht");

        CounterTrip later = new CounterTrip(dcEnd.plus(Duration.ofMinutes(61)), BigDecimal.valueOf(90), BigDecimal.valueOf(50), BigDecimal.valueOf(41.6));
        List<CounterTrip> list2 = new ArrayList<>(base);
        list2.add(later);
        Optional<Detection> d = VehicleCounterSohDetector.detect(list2, List.of(dcEnd));
        assertEquals(5, d.orElseThrow().tripCount());
    }
}
