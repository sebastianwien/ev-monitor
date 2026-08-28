package com.evmonitor.domain.route;

import java.util.ArrayList;
import java.util.List;

/**
 * Liest das Encoded Polyline Format (Google, 5 Nachkommastellen) - dieselbe Sprache, in der
 * der connectors-service die gefahrene Spur schickt und openrouteservice seine Routen liefert.
 *
 * <p>Nur Lesen: geschrieben wird das Format hier nirgends, die Linien entstehen anderswo.
 */
public final class EncodedPolyline {

    private EncodedPolyline() {}

    /**
     * @return Punkte als {@code [lat, lon]}; eine leere Liste bei leerer oder unlesbarer
     *         Eingabe. Bricht nie ab - eine kaputte Linie ist ein Darstellungsproblem,
     *         kein Grund, den Vorgang darum herum scheitern zu lassen.
     */
    public static List<double[]> decode(String encoded) {
        List<double[]> points = new ArrayList<>();
        if (encoded == null || encoded.isBlank()) return points;

        int index = 0;
        int lat = 0;
        int lon = 0;
        while (index < encoded.length()) {
            int[] deltas = new int[2];
            for (int coordinate = 0; coordinate < 2; coordinate++) {
                int result = 0;
                int shift = 0;
                int b;
                do {
                    if (index >= encoded.length()) return points;  // mitten in einer Zahl zu Ende
                    b = encoded.charAt(index++) - 63;
                    if (b < 0 || b > 0x3f) return points;          // ausserhalb des Alphabets
                    result |= (b & 0x1f) << shift;
                    shift += 5;
                } while (b >= 0x20);
                deltas[coordinate] = (result & 1) != 0 ? ~(result >> 1) : result >> 1;
            }
            lat += deltas[0];
            lon += deltas[1];
            points.add(new double[]{lat / 1e5, lon / 1e5});
        }
        return points;
    }

    /**
     * Duennt gleichmaessig auf hoechstens {@code max} Punkte aus; Anfang und Ende bleiben
     * stehen. Kuerzere Linien werden unveraendert zurueckgegeben.
     */
    public static List<double[]> thin(List<double[]> points, int max) {
        if (max < 2) throw new IllegalArgumentException("max must be >= 2");
        if (points.size() <= max) return points;

        List<double[]> thinned = new ArrayList<>(max);
        double step = (points.size() - 1) / (double) (max - 1);
        for (int i = 0; i < max; i++) {
            thinned.add(points.get((int) Math.round(i * step)));
        }
        return thinned;
    }
}
