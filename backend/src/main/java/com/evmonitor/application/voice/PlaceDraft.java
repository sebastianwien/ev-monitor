package com.evmonitor.application.voice;

import com.evmonitor.application.NearbyStation;
import com.evmonitor.domain.ChargingSiteUsage;

/**
 * Der erkannte Ort in der Form der {@code PlaceChoice} im Wizard: home, station (Register-Treffer
 * der Umkreissuche), site (bekannter Standort des Nutzers) oder other (gesprochener Betreiber).
 */
public record PlaceDraft(String kind, NearbyStation station, ChargingSiteUsage site, String cpoName) {

    static PlaceDraft home() {
        return new PlaceDraft("home", null, null, null);
    }

    static PlaceDraft other(String cpoName) {
        return new PlaceDraft("other", null, null, cpoName);
    }

    static PlaceDraft of(PlaceCandidate candidate) {
        return switch (candidate) {
            case PlaceCandidate.Home h -> home();
            case PlaceCandidate.Station s -> new PlaceDraft("station", s.station(), null, null);
            case PlaceCandidate.Site s -> new PlaceDraft("site", null, s.usage(), null);
        };
    }
}
