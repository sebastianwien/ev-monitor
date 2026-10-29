package com.evmonitor.application;

import ch.hsr.geohash.GeoHash;
import com.evmonitor.domain.ChargingType;
import com.evmonitor.domain.EvLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Öffentlich oder privat, wenn die Quelle es nicht weiß. Connectoren kennen nur die Position, die
 * Historie des Nutzers kennt nur der Core - daher entscheidet er hier, vor der Preisübernahme.
 *
 * <p>Default öffentlich (7 Stellen, rund 150 m). Hat der Nutzer im 6er-Feld (rund 600 m) schon
 * ausdrücklich privat geladen, ist eine neue AC-Ladung dort privat und wird auf 6 Stellen gekürzt -
 * außer sie trifft eine Zelle, in der er schon öffentlich geladen hat (die Säule neben der
 * Wohnung). DC ist immer öffentlich. Was die Quelle ausdrücklich meldet, bleibt.
 */
@Component
@RequiredArgsConstructor
public class ChargingPlaceClassifier {

    private static final int PRIVATE_PRECISION = 6;
    private static final int PUBLIC_PRECISION = 7;

    private final EvLogRepository evLogRepository;

    /** Ort und Einstufung, wie sie gespeichert werden. {@code publicCharging} null heißt weiter unbekannt. */
    public record ChargingPlace(String geohash, Boolean publicCharging) {}

    public ChargingPlace classify(UUID userId, String geohash, ChargingType chargingType, Boolean publicCharging) {
        if (geohash == null) return new ChargingPlace(null, publicCharging);
        if (Boolean.FALSE.equals(publicCharging)) return new ChargingPlace(truncate(geohash, PRIVATE_PRECISION), false);
        if (Boolean.TRUE.equals(publicCharging) || chargingType == ChargingType.DC) return new ChargingPlace(geohash, true);
        if (geohash.length() < PRIVATE_PRECISION) return new ChargingPlace(geohash, null);

        String field = truncate(geohash, PRIVATE_PRECISION);
        boolean privateField = evLogRepository.existsPrivateLogInCell(userId, field);
        // Nur 6 Stellen (alte Connectoren, Altimporte): ohne 7er-Zelle trifft der Ort keine Säule,
        // öffentlich darf er dann nicht werden - sonst bepreiste ihn irgendeine Ladung im Feld.
        if (geohash.length() < PUBLIC_PRECISION) return new ChargingPlace(geohash, privateField ? false : null);

        String cell = truncate(geohash, PUBLIC_PRECISION);
        if (!privateField || evLogRepository.existsPublicLogInCells(userId, cellAndNeighbours(cell))) {
            return new ChargingPlace(cell, true);
        }
        return new ChargingPlace(field, false);
    }

    /** Eigene Zelle plus die acht angrenzenden: eine Säule an der Zellgrenze trifft sonst nur jede zweite Ladung. */
    private static List<String> cellAndNeighbours(String cell) {
        List<String> cells = new ArrayList<>(9);
        cells.add(cell);
        for (GeoHash adjacent : GeoHash.fromGeohashString(cell).getAdjacent()) cells.add(adjacent.toBase32());
        return cells;
    }

    private static String truncate(String geohash, int precision) {
        return geohash.length() > precision ? geohash.substring(0, precision) : geohash;
    }
}
