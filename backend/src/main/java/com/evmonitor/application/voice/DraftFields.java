package com.evmonitor.application.voice;

import com.evmonitor.domain.ChargingType;
import com.evmonitor.domain.RouteType;
import com.evmonitor.domain.TireType;
import lombok.Builder;

import java.util.List;
import java.util.stream.Stream;

/**
 * Die Felder, die das Sprachmodell aus dem Transkript gelesen hat - Produktiv-Schema des Sprachlogs
 *. Alles ist optional: was nicht gesagt wurde, bleibt null.
 *
 * @param placeIndex     Index in der Kandidatenliste dieses Requests, nicht stabil ueber Requests
 * @param placeKind      home, station oder other
 * @param spokenOperator gesprochener Betreiber, wenn kein Kandidat passt
 * @param tariffIndex    Index in den eigenen Tarifen des Nutzers
 * @param loggedAt       lokale Zeit "YYYY-MM-DDTHH:MM" in der Zeitzone des Nutzers
 * @param uncertain      Feldnamen, bei denen Modell oder Plausibilisierung zweifeln
 */
@Builder(toBuilder = true)
public record DraftFields(Integer placeIndex, String placeKind, String spokenOperator, Integer tariffIndex,
                          Double kwhCharged, Double kwhAtVehicle, Integer socBefore, Integer socAfter,
                          Integer odometerKm, Double costEur, Double pricePerKwh, String loggedAt,
                          Integer chargeDurationMinutes, Double maxChargingPowerKw, ChargingType chargingType,
                          RouteType routeType, TireType tireType, List<String> uncertain) {

    public DraftFields {
        uncertain = uncertain == null ? List.of() : List.copyOf(uncertain);
    }

    /** Wie viele Angaben gesprochen wurden - fuer die Nutzungsstatistik, Ort zaehlt einmal. */
    public int filledCount() {
        boolean place = placeIndex != null || placeKind != null;
        return (place ? 1 : 0) + (int) Stream.of(tariffIndex, kwhCharged, kwhAtVehicle, socBefore, socAfter, odometerKm,
                costEur, pricePerKwh, loggedAt, chargeDurationMinutes, maxChargingPowerKw, chargingType, routeType, tireType)
                .filter(v -> v != null).count();
    }
}
