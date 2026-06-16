package com.evmonitor.domain;

/**
 * Subscription tiers for paid features. Drives Stripe price-id routing,
 * Tesla telemetry profile selection, and trip-push entitlement.
 *
 * <ul>
 *   <li>{@link #NONE} - free tier (default for new users)</li>
 *   <li>{@link #AUTOSYNC} - charging-only sync (Smartcar webhooks, Tesla CHARGING_ONLY profile)</li>
 *   <li>{@link #AUTOSYNC_LIVE} - everything plus Tesla FULL profile (trip-push, phantom-drain)</li>
 *   <li>{@link #SUPPORTER} - analytics-only upsell: unlocks the premium analytics view
 *       (energy split, phantom drain, power curves) without any telemetry/AutoSync
 *       entitlement. Orthogonal to the AutoSync ladder, not above or below it.</li>
 * </ul>
 */
public enum SubscriptionTier {
    NONE,
    AUTOSYNC,
    AUTOSYNC_LIVE,
    SUPPORTER;

    /** Whether this is a paid subscription (billing sense). SUPPORTER is paid. */
    public boolean isPaid() {
        return this != NONE;
    }

    /**
     * Whether this tier grants the telemetry/AutoSync entitlement (drives the legacy
     * {@code premium} flag and {@code canActivateTelemetry}). SUPPORTER pays only for the
     * analytics view and must NOT unlock telemetry activation, so it is excluded here.
     */
    public boolean grantsTelemetry() {
        return this == AUTOSYNC || this == AUTOSYNC_LIVE;
    }
}
