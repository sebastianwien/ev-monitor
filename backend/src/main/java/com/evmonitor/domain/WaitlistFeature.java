package com.evmonitor.domain;

/**
 * Features, fuer die sich ein User vorab auf eine Warteliste setzen kann
 * (Opt-in "benachrichtige mich, sobald verfuegbar"). Bewusst als kontrollierte
 * Allowlist - nur diese Werte duerfen in {@code feature_waitlist} landen.
 */
public enum WaitlistFeature {
    /** Automatischer XPeng-Import (EU Data Act) - loest den manuellen ZIP-Upload ab. */
    XPENG_AUTOSYNC
}
