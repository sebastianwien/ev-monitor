package com.evmonitor.domain;

public enum LeaderboardCategory {

    MONTHLY_KWH("Meiste kWh", "kWh", false, true),
    MONTHLY_CHARGES("Meiste Ladevorgänge", "Ladevorgänge", false, true),
    MONTHLY_DISTANCE("Längste Strecke", "km", false, true),
    MONTHLY_COINS("Meisten Watt", "Watt", false, true),
    MONTHLY_CHEAPEST("Günstigster Lader", "ct/kWh", true, true),
    MONTHLY_NIGHT_OWL("Nacht-Eule", "Nacht-Ladungen", false, false),
    MONTHLY_ICE_CHARGER("Eisbär des Monats", "°C", true, false),
    MONTHLY_POWER_CHARGER("Schnellster Lader", "kW", false, false);

    private final String displayName;
    private final String unit;
    /**
     * true = lower value is better (e.g. cheapest = lowest ct/kWh, coldest = lowest temperature)
     */
    private final boolean lowerIsBetter;
    /**
     * true = top 3 receive bonus coins at end of month
     */
    private final boolean hasMonthEndReward;

    LeaderboardCategory(String displayName, String unit, boolean lowerIsBetter, boolean hasMonthEndReward) {
        this.displayName = displayName;
        this.unit = unit;
        this.lowerIsBetter = lowerIsBetter;
        this.hasMonthEndReward = hasMonthEndReward;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getUnit() {
        return unit;
    }

    public boolean isLowerIsBetter() {
        return lowerIsBetter;
    }

    public boolean isHasMonthEndReward() {
        return hasMonthEndReward;
    }
}
