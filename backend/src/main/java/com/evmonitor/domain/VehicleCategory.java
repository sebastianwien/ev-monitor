package com.evmonitor.domain;

public enum VehicleCategory {
    CITY_CAR("Kleinwagen"),
    COMPACT("Kompakt"),
    SEDAN("Mittelklasse"),
    SUV("SUV"),
    LARGE_SUV("Großer SUV"),
    LUXURY("Oberklasse"),
    SPORTS("Sportwagen"),
    VAN("Van"),
    PICKUP("Pickup");

    private final String displayName;

    VehicleCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
