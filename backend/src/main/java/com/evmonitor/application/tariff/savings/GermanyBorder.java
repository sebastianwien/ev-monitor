package com.evmonitor.application.tariff.savings;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * Liegt ein Ort in Deutschland? Grenze aus Natural Earth 1:50m (gemeinfrei, über world-atlas),
 * etwa ein bis zwei Kilometer genau, als Ringe aus [Länge, Breite] in {@code geo/germany-ne50m.json}.
 * Für die Frage "gilt ein deutscher Ladetarif" reicht das; direkt an der Grenze kann es danebenliegen.
 */
public final class GermanyBorder {

    private static final String BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz";
    private static final double[][][] RINGS = load();

    private GermanyBorder() {}

    public static boolean contains(double lat, double lon) {
        for (double[][] ring : RINGS) {
            if (inside(ring, lon, lat)) return true;
        }
        return false;
    }

    /** Mitte des Geohash-Felds; ohne oder mit ungültigem Geohash false. */
    public static boolean containsGeohash(String geohash) {
        if (geohash == null || geohash.isBlank()) return false;
        double[] lat = {-90, 90};
        double[] lon = {-180, 180};
        boolean even = true;
        for (char c : geohash.toLowerCase().toCharArray()) {
            int v = BASE32.indexOf(c);
            if (v < 0) return false;
            for (int bit = 4; bit >= 0; bit--) {
                double[] range = even ? lon : lat;
                double mid = (range[0] + range[1]) / 2;
                if (((v >> bit) & 1) == 1) range[0] = mid;
                else range[1] = mid;
                even = !even;
            }
        }
        return contains((lat[0] + lat[1]) / 2, (lon[0] + lon[1]) / 2);
    }

    /** Strahlverfahren. */
    private static boolean inside(double[][] ring, double x, double y) {
        boolean in = false;
        for (int i = 0, j = ring.length - 1; i < ring.length; j = i++) {
            double xi = ring[i][0], yi = ring[i][1], xj = ring[j][0], yj = ring[j][1];
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) in = !in;
        }
        return in;
    }

    private static double[][][] load() {
        try (InputStream in = GermanyBorder.class.getResourceAsStream("/geo/germany-ne50m.json")) {
            if (in == null) throw new IllegalStateException("geo/germany-ne50m.json fehlt");
            return new ObjectMapper().readValue(in, double[][][].class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
