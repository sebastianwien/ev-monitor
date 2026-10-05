package com.evmonitor.application.voice;

import com.evmonitor.domain.SubscriptionTier;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;

/**
 * Monatsdeckel fuer Sprachaufnahmen. Free 5 je Kalendermonat, im ersten Monat nach der ersten
 * Aufnahme 10 (Kennenlernphase). Bezahlte Tiers (auch Supporter) 30, sichtbar. Admins (Testbetrieb
 * auf Prod) sehen keinen Deckel, haben aber ein stilles Fair-Use-Limit. Gezaehlt werden nur
 * erfolgreiche Aufnahmen; alles ergibt sich aus {@code voice_draft}, es gibt keinen gespeicherten Zustand.
 *
 * @param plan      bestimmt den Deckel und im Frontend den Hinweis (Upgrade nur bei FREE)
 * @param limit     sichtbarer Deckel, null bei Admins
 * @param remaining verbleibend bis zum sichtbaren Deckel, null bei Admins
 * @param resetsOn  erster Tag des naechsten Monats in der Zeitzone des Nutzers
 */
public record VoiceQuota(Plan plan, Integer limit, Integer remaining, boolean exhausted, LocalDate resetsOn) {

    public enum Plan { FREE, PAID, ADMIN }

    static final int FREE_LIMIT = 5;
    static final int FREE_INTRO_LIMIT = 10;
    static final int PAID_LIMIT = 30;
    static final int ADMIN_FAIR_USE_LIMIT = 60;

    static Plan plan(SubscriptionTier tier, String role) {
        if ("ADMIN".equals(role)) return Plan.ADMIN;
        return tier != null && tier.isPaid() ? Plan.PAID : Plan.FREE;
    }

    static VoiceQuota of(Plan plan, long usedThisMonth, LocalDateTime firstUseAt, ZonedDateTime now) {
        LocalDate resetsOn = now.toLocalDate().with(TemporalAdjusters.firstDayOfNextMonth());
        if (plan == Plan.ADMIN) {
            return new VoiceQuota(plan, null, null, usedThisMonth >= ADMIN_FAIR_USE_LIMIT, resetsOn);
        }
        boolean intro = firstUseAt == null || now.toLocalDateTime().isBefore(firstUseAt.plusMonths(1));
        int limit = plan == Plan.PAID ? PAID_LIMIT : intro ? FREE_INTRO_LIMIT : FREE_LIMIT;
        int remaining = (int) Math.max(0, limit - usedThisMonth);
        return new VoiceQuota(plan, limit, remaining, remaining == 0, resetsOn);
    }

    /** Stand nach dieser Aufnahme, fuer die Anzeige "2 von 5 diesen Monat". */
    public VoiceQuota afterUse() {
        if (remaining == null) return this;
        int left = Math.max(0, remaining - 1);
        return new VoiceQuota(plan, limit, left, left == 0, resetsOn);
    }

    static ZonedDateTime monthStart(ZonedDateTime now) {
        return now.toLocalDate().withDayOfMonth(1).atStartOfDay(now.getZone());
    }
}
