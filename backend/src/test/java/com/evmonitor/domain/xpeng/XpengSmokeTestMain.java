package com.evmonitor.domain.xpeng;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Manueller Smoke-Test - laeuft den XPeng-Parser + Detectoren gegen lokale,
 * entschluesselte Files und gibt Trips/Sessions/Extras-JSON aus.
 *
 * Aufruf:
 *   ./gradlew test --tests "com.evmonitor.domain.xpeng.XpengSmokeTestMain.smoke" \
 *       -DxpengSmoke=1 [-DxpengSmokeDir=/tmp/xpeng-files]
 *
 * @Disabled by default - haengt von lokalen Files ab, gehoert nicht in CI.
 */
public class XpengSmokeTestMain {

    @Test
    @EnabledIfEnvironmentVariable(named = "XPENG_SMOKE", matches = "1")
    void smoke() throws Exception {
        String dir = System.getenv().getOrDefault("XPENG_SMOKE_DIR", "/tmp/xpeng-files");
        runAll(dir);
    }

    private static void runAll(String dir) throws Exception {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.list(Paths.get(dir))) {
            stream.filter(p -> p.getFileName().toString().startsWith("dec_")
                            && p.getFileName().toString().endsWith(".xlsx"))
                  .sorted()
                  .forEach(files::add);
        }
        if (files.isEmpty()) {
            System.err.println("Kein dec_*.xlsx in " + dir + " gefunden.");
            return;
        }

        ObjectMapper mapper = new ObjectMapper();
        XpengExcelStreamingParser parser = new XpengExcelStreamingParser();

        for (Path f : files) {
            System.out.println("\n========================================");
            System.out.println("FILE: " + f.getFileName() + "  (" + Files.size(f) / 1_000_000 + " MB)");
            System.out.println("========================================");

            XpengTripDetector tripDetector = new XpengTripDetector();
            XpengChargeDetector chargeDetector = new XpengChargeDetector();
            List<DetectedTrip> trips = new ArrayList<>();
            List<DetectedChargingSession> sessions = new ArrayList<>();

            try {
                long t0 = System.currentTimeMillis();
                var result = parser.parse(f, null, row -> {
                    tripDetector.consume(row).ifPresent(trips::add);
                    chargeDetector.consume(row).ifPresent(sessions::add);
                });
                tripDetector.finish().ifPresent(trips::add);
                chargeDetector.finish().ifPresent(sessions::add);
                long ms = System.currentTimeMillis() - t0;

                System.out.println("VIN: " + result.vehicleInfo().vin()
                        + "  Model: " + result.vehicleInfo().model()
                        + "  Rows: " + result.rowsProcessed()
                        + "  Parse: " + ms + " ms");
                System.out.println("Trips:    " + trips.size());
                System.out.println("Sessions: " + sessions.size());

                long sessionsWithExtras = sessions.stream().filter(s -> s.telemetryExtras() != null).count();
                long tripsWithExtras    = trips.stream().filter(t -> t.telemetryExtras() != null).count();
                System.out.println("Sessions mit extras: " + sessionsWithExtras + "/" + sessions.size());
                System.out.println("Trips mit extras:    " + tripsWithExtras + "/" + trips.size());

                if (!sessions.isEmpty()) {
                    var s = sessions.get(0);
                    System.out.println("\n  Erste Session (" + s.chargingType() + ", maxPower=" + s.maxPowerKw() + " kW):");
                    System.out.println("    kwh=" + s.kwhCharged() + ", soc=" + s.socStart() + "->" + s.socEnd());
                    if (s.telemetryExtras() != null) {
                        System.out.println("    extras=" + mapper.writerWithDefaultPrettyPrinter()
                                .writeValueAsString(s.telemetryExtras()));
                    }
                }
                if (!trips.isEmpty()) {
                    var t = trips.get(0);
                    System.out.println("\n  Erste Fahrt (" + t.distanceKm() + " km):");
                    System.out.println("    soc=" + t.socStart() + "->" + t.socEnd()
                            + ", maxSpeed=" + t.maxSpeedKmh());
                    if (t.telemetryExtras() != null) {
                        System.out.println("    extras=" + mapper.writerWithDefaultPrettyPrinter()
                                .writeValueAsString(t.telemetryExtras()));
                    }
                }
            } catch (Exception e) {
                System.err.println("FAILED: " + e.getMessage());
            }
        }
    }
}
