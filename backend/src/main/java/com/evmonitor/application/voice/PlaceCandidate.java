package com.evmonitor.application.voice;

import com.evmonitor.application.NearbyStation;
import com.evmonitor.domain.ChargingSiteUsage;

/**
 * Ein Ort, den das Modell per Index waehlen kann. Kandidaten haben keine stabile ID, darum
 * nummeriert der Service sie je Request durch. Fuer den Prompt gibt es nur home und station,
 * so wurde Prompt v3 gemessen; ob es ein Register-Treffer oder ein bekannter Standort ist,
 * weiss nur der Service.
 */
public sealed interface PlaceCandidate {

    String name();

    default String promptKind() {
        return this instanceof Home ? "home" : "station";
    }

    record Home() implements PlaceCandidate {
        public String name() {
            return "Zuhause";
        }
    }

    record Station(NearbyStation station) implements PlaceCandidate {
        public String name() {
            return station.name();
        }
    }

    record Site(ChargingSiteUsage usage) implements PlaceCandidate {
        public String name() {
            return usage.site().name();
        }
    }
}
