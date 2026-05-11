package com.evmonitor.domain.xpeng;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test against the real (decrypted) XPeng xlsx in /tmp.
 * Skipped on CI - only runs locally when the file is present.
 */
class XpengExcelStreamingParserIT {

    static boolean realFileAvailable() {
        return Files.exists(Path.of("/tmp/xpeng_decrypted.xlsx"));
    }

    @Test
    @EnabledIf("realFileAvailable")
    void parsesRealFile() throws Exception {
        Path file = Path.of("/tmp/xpeng_decrypted.xlsx");
        XpengExcelStreamingParser parser = new XpengExcelStreamingParser();
        XpengTripDetector tripDet = new XpengTripDetector();
        XpengChargeDetector chargeDet = new XpengChargeDetector();
        List<DetectedTrip> trips = new ArrayList<>();
        List<DetectedChargingSession> sessions = new ArrayList<>();
        int[] rowCount = {0};

        long t0 = System.nanoTime();
        XpengExcelStreamingParser.ParseResult result = parser.parse(file, null, row -> {
            rowCount[0]++;
            tripDet.consume(row).ifPresent(trips::add);
            chargeDet.consume(row).ifPresent(sessions::add);
        });
        tripDet.finish().ifPresent(trips::add);
        chargeDet.finish().ifPresent(sessions::add);
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;

        assertNotNull(result.vehicleInfo());
        assertNotNull(result.vehicleInfo().vin());
        assertEquals(17, result.vehicleInfo().vin().length(), "VIN should be 17 chars");
        assertTrue(rowCount[0] > 100_000, "expect 100k+ rows in 15 days of 5s sampling, got " + rowCount[0]);
        assertTrue(elapsedMs < 60_000, "parsing should complete in <60s, took " + elapsedMs + "ms");
        System.out.println("VIN: " + result.vehicleInfo().vin().substring(0, 4) + "***");
        System.out.println("Model: " + result.vehicleInfo().model());
        System.out.println("Rows: " + rowCount[0] + " in " + elapsedMs + "ms");
        System.out.println("Trips detected: " + trips.size());
        System.out.println("Charging sessions detected: " + sessions.size());
        if (!trips.isEmpty()) {
            DetectedTrip first = trips.get(0);
            System.out.println("First trip: " + first.startedAt() + " → " + first.endedAt()
                    + ", " + first.distanceKm() + " km, "
                    + first.socStart() + "% → " + first.socEnd() + "%");
        }
        if (!sessions.isEmpty()) {
            DetectedChargingSession first = sessions.get(0);
            System.out.println("First session: " + first.startedAt() + " → " + first.endedAt()
                    + ", " + first.kwhCharged() + " kWh, max " + first.maxPowerKw() + " kW ("
                    + first.chargingType() + ")");
        }
        assertTrue(trips.size() > 0, "expected at least 1 trip in 15 days of usage");
    }
}
