package com.evmonitor.application.voice;

import com.evmonitor.domain.SubscriptionTier;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;

/**
 * Monatsdeckel fuer Sprachaufnahmen. Free 5 je Kalendermonat, im ersten Monat nach der ersten
 * Aufnahme 10 (Kennenlernphase). Bezahlte Tiers und Admins (Testbetrieb auf Prod) sehen keinen
 * Deckel, haben aber ein stilles Fair-Use-Limit. Gezaehlt werden nur erfolgreiche Aufnahmen; alles ergibt sich aus {@code voice_draft},
 * es gibt keinen gespeicherten Zustand.
 *
 * @param limit     sichtbarer Deckel, null bei bezahlten Tiers
 * @param remaining verbleibend bis zum sichtbaren Deckel, null bei bezahlten Tiers
 * @param resetsOn  erster Tag des naechsten Monats in der Zeitzone des Nutzers
 */
public record VoiceQuota(Integer limit, Integer remaining, boolean exhausted, LocalDate resetsOn) {

    static final int FREE_LIMIT = 5;
    static final int FREE_INTRO_LIMIT = 10;
    static final int FAIR_USE_LIMIT = 60;

    static boolean fairUseOnly(SubscriptionTier tier, String role) {
        return (tier != null && tier.isPaid()) || "ADMIN".equals(role);
    }

    static VoiceQuota of(boolean fairUseOnly, long usedThisMonth, LocalDateTime firstUseAt, ZonedDateTime now) {
        LocalDate resetsOn = now.toLocalDate().with(TemporalAdjusters.firstDayOfNextMonth());
        if (fairUseOnly) {
            return new VoiceQuota(null, null, usedThisMonth >= FAIR_USE_LIMIT, resetsOn);
        }
        boolean intro = firstUseAt == null || now.toLocalDateTime().isBefore(firstUseAt.plusMonths(1));
        int limit = intro ? FREE_INTRO_LIMIT : FREE_LIMIT;
        int remaining = (int) Math.max(0, limit - usedThisMonth);
        return new VoiceQuota(limit, remaining, remaining == 0, resetsOn);
    }

    /** Stand nach dieser Aufnahme, fuer die Anzeige "2 von 5 diesen Monat". */
    public VoiceQuota afterUse() {
        if (remaining == null) return this;
        int left = Math.max(0, remaining - 1);
        return new VoiceQuota(limit, left, left == 0, resetsOn);
    }

    static ZonedDateTime monthStart(ZonedDateTime now) {
        return now.toLocalDate().withDayOfMonth(1).atStartOfDay(now.getZone());
    }
}
