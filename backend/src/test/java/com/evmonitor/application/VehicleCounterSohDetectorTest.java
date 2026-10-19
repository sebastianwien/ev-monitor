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

    /** n Fahrten mit je {@code hub} Prozentpunkten und {@code kwhPerPercent} kWh je Prozent, eine je Tag. */
    private static List<CounterTrip> trips(int n, int hub, double kwhPerPercent) {
        List<CounterTrip> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(new CounterTrip(T0.plus(Duration.ofDays(i)), BigDecimal.valueOf(80), BigDecimal.valueOf(80 - hub),
                    BigDecimal.valueOf(hub * kwhPerPercent)));
        }
        return out;
    }

    @Test
    void sumsEnergyOverSocAcrossTrips() {
        // 20 Fahrten à 15 %, 1,04 kWh je Prozent (P3-Messung) -> 104 kWh nutzbar, Hub 300
        Optional<Detection> d = VehicleCounterSohDetector.detect(trips(20, 15, 1.04), List.of());
        assertTrue(d.isPresent());
        assertEquals(new BigDecimal("104.00"), d.get().capacityKwh());
        assertEquals(20, d.get().tripCount());
        assertEquals(new BigDecimal("300"), d.get().totalHubPercent());
    }

    @Test
    void needsAtLeastTwentyTripsEvenWithLargeHub() {
        assertTrue(VehicleCounterSohDetector.detect(trips(19, 50, 1.04), List.of()).isEmpty());
    }

    @Test
    void needsThreeHundredPercentTotalHub() {
        assertTrue(VehicleCounterSohDetector.detect(trips(25, 10, 1.04), List.of()).isEmpty(), "250 %");
        assertTrue(VehicleCounterSohDetector.detect(trips(30, 10, 1.04), List.of()).isPresent(), "300 %");
    }

    @Test
    void ignoresTripsWithoutSocDropOrWithoutCounter() {
        List<CounterTrip> list = new ArrayList<>(trips(20, 15, 1.04));
        list.add(new CounterTrip(T0.plus(Duration.ofDays(30)), BigDecimal.valueOf(68), BigDecimal.valueOf(68), BigDecimal.valueOf(0.4)));
        list.add(new CounterTrip(T0.plus(Duration.ofDays(31)), BigDecimal.valueOf(68), BigDecimal.valueOf(60), null));
        list.add(new CounterTrip(T0.plus(Duration.ofDays(32)), null, BigDecimal.valueOf(60), BigDecimal.valueOf(8)));
        Detection d = VehicleCounterSohDetector.detect(list, List.of()).orElseThrow();
        assertEquals(20, d.tripCount());
        assertEquals(new BigDecimal("104.00"), d.capacityKwh());
    }

    @Test
    void excludesTripsStartingWithinAnHourAfterDcCharge() {
        List<CounterTrip> list = new ArrayList<>(trips(20, 15, 1.04));
        // Ausreißer direkt nach DC: 5 % Abfall bei 1 kWh (SoC setzt sich), würde die Kapazität drücken
        Instant dcEnd = T0.plus(Duration.ofDays(40));
        list.add(new CounterTrip(dcEnd.plus(Duration.ofMinutes(20)), BigDecimal.valueOf(90), BigDecimal.valueOf(85), BigDecimal.valueOf(1.0)));
        Detection d = VehicleCounterSohDetector.detect(list, List.of(dcEnd)).orElseThrow();
        assertEquals(20, d.tripCount());
        assertEquals(new BigDecimal("104.00"), d.capacityKwh());

        // Dieselbe Fahrt eine Stunde später zählt
        List<CounterTrip> later = new ArrayList<>(trips(20, 15, 1.04));
        later.add(new CounterTrip(dcEnd.plus(Duration.ofMinutes(61)), BigDecimal.valueOf(90), BigDecimal.valueOf(85), BigDecimal.valueOf(1.0)));
        assertEquals(21, VehicleCounterSohDetector.detect(later, List.of(dcEnd)).orElseThrow().tripCount());
    }
}
