package com.evmonitor.domain;

/**
 * Types of coins that can be earned through various actions.
 * Each type represents a different category of achievement or behavior.
 */
public enum CoinType {
    /**
     * Green Coins: Earned for eco-friendly driving and sustainable behavior
     * Examples: ECO driving style, efficient consumption, low carbon footprint
     */
    GREEN_COIN("Green Coin", "Eco-friendly driving rewards"),

    /**
     * Distance Coins: Earned for logging drives and accumulating mileage
     * Examples: First drive logged, milestone distances (100km, 1000km, etc.)
     */
    DISTANCE_COIN("Distance Coin", "Mileage tracking rewards"),

    /**
     * Social Coins: Earned for community engagement and sharing
     * Examples: Location sharing, first car added, profile completion
     */
    SOCIAL_COIN("Social Coin", "Community engagement rewards"),

    /**
     * Streak Coins: Earned for consistent daily/weekly activity
     * Examples: Daily login streak, weekly logging streak
     */
    STREAK_COIN("Streak Coin", "Consistency rewards"),

    /**
     * Achievement Coins: Earned for special milestones and rare accomplishments
     * Examples: 100% battery efficiency, extreme weather driving, first charge
     */
    ACHIEVEMENT_COIN("Achievement Coin", "Special milestone rewards"),

    /**
     * Efficiency Coins: Earned for optimizing consumption and range
     * Examples: Below-average consumption, maximum range achieved
     */
    EFFICIENCY_COIN("Efficiency Coin", "Optimization rewards");

    private final String displayName;
    private final String description;

    CoinType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
