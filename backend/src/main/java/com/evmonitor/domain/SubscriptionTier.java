package com.evmonitor.domain;

/**
 * Subscription tiers for paid features. Drives Stripe price-id routing,
 * Tesla telemetry profile selection, and trip-push entitlement.
 *
 * <ul>
 *   <li>{@link #NONE} - free tier (default for new users)</li>
 *   <li>{@link #AUTOSYNC} - charging-only sync (Smartcar webhooks, Tesla CHARGING_ONLY profile)</li>
 *   <li>{@link #AUTOSYNC_LIVE} - everything plus Tesla FULL profile (trip-push, phantom-drain)</li>
 * </ul>
 */
public enum SubscriptionTier {
    NONE,
    AUTOSYNC,
    AUTOSYNC_LIVE;

    public boolean isPaid() {
        return this != NONE;
    }
}
