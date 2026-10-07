package com.evmonitor.domain;

import java.math.BigDecimal;

/**
 * Fahrzeugklasse eines Modells. Die Literzahl ist der Realverbrauch eines Benziners dieser
 * Klasse (l/100 km) für den Vergleich "was hätte ein Verbrenner getankt": Mittelwerte
 * veröffentlichter Realverbrauchsspannen (Spritmonitor-Community, EU OBFCM rund 20 Prozent
 * über WLTP); Sportwagen, Van und Pickup sind redaktionelle Schätzungen. Dieselbe Tabelle
 * steht im Frontend in costMix.ts (COMBUSTION_LITERS_BY_CLASS).
 */
public enum VehicleCategory {
    CITY_CAR("Kleinwagen", "5.8"),
    COMPACT("Kompakt", "6.8"),
    SEDAN("Mittelklasse", "7.6"),
    SUV("SUV", "8.4"),
    LARGE_SUV("Großer SUV", "10.5"),
    LUXURY("Oberklasse", "9.8"),
    SPORTS("Sportwagen", "10.5"),
    VAN("Van", "8.5"),
    PICKUP("Pickup", "11.0");

    private final String displayName;
    private final BigDecimal petrolLitersPer100Km;

    VehicleCategory(String displayName, String petrolLitersPer100Km) {
        this.displayName = displayName;
        this.petrolLitersPer100Km = new BigDecimal(petrolLitersPer100Km);
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Realverbrauch eines Benziners dieser Klasse in l/100 km */
    public BigDecimal getPetrolLitersPer100Km() {
        return petrolLitersPer100Km;
    }
}
