package com.evmonitor.application.voice;

import com.evmonitor.domain.SubscriptionTier;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** Deckel: Free 5 je Monat, erster Monat ab erster Aufnahme 10, bezahlte Tiers sichtbar 30, Admins still 60. */
class VoiceQuotaTest {

    private static final ZonedDateTime NOW = ZonedDateTime.of(2026, 10, 3, 15, 0, 0, 0, ZoneId.of("Europe/Berlin"));

    @Test
    void freeUserBeforeFirstRecordingIsInTheIntroMonth() {
        VoiceQuota q = VoiceQuota.of(VoiceQuota.Plan.FREE, 0, null, NOW);

        assertThat(q.limit()).isEqualTo(10);
        assertThat(q.remaining()).isEqualTo(10);
        assertThat(q.exhausted()).isFalse();
    }

    @Test
    void freeUserWithinFirstMonthAfterFirstRecordingGetsTen() {
        VoiceQuota q = VoiceQuota.of(VoiceQuota.Plan.FREE, 7, LocalDateTime.of(2026, 9, 20, 10, 0), NOW);

        assertThat(q.limit()).isEqualTo(10);
        assertThat(q.remaining()).isEqualTo(3);
    }

    @Test
    void freeUserAfterTheIntroMonthGetsFive() {
        VoiceQuota q = VoiceQuota.of(VoiceQuota.Plan.FREE, 5, LocalDateTime.of(2026, 9, 2, 10, 0), NOW);

        assertThat(q.limit()).isEqualTo(5);
        assertThat(q.remaining()).isZero();
        assertThat(q.exhausted()).isTrue();
    }

    @Test
    void resetsOnTheFirstOfNextMonthInTheUsersZone() {
        assertThat(VoiceQuota.of(VoiceQuota.Plan.FREE, 0, null, NOW).resetsOn()).isEqualTo(LocalDate.of(2026, 11, 1));
    }

    @Test
    void paidTiersSeeAVisibleLimitOfThirty() {
        VoiceQuota open = VoiceQuota.of(VoiceQuota.Plan.PAID, 29, LocalDateTime.of(2026, 1, 1, 0, 0), NOW);
        VoiceQuota capped = VoiceQuota.of(VoiceQuota.Plan.PAID, 30, LocalDateTime.of(2026, 1, 1, 0, 0), NOW);

        assertThat(open.limit()).isEqualTo(30);
        assertThat(open.remaining()).isEqualTo(1);
        assertThat(open.plan()).isEqualTo(VoiceQuota.Plan.PAID);
        assertThat(capped.exhausted()).isTrue();
    }

    @Test
    void adminsHaveNoVisibleLimitButASilentCap() {
        VoiceQuota open = VoiceQuota.of(VoiceQuota.Plan.ADMIN, 59, null, NOW);
        VoiceQuota capped = VoiceQuota.of(VoiceQuota.Plan.ADMIN, 60, null, NOW);

        assertThat(open.limit()).isNull();
        assertThat(open.remaining()).isNull();
        assertThat(open.exhausted()).isFalse();
        assertThat(capped.exhausted()).isTrue();
    }

    @Test
    void planFollowsRoleThenTier() {
        for (SubscriptionTier tier : new SubscriptionTier[]{SubscriptionTier.SUPPORTER, SubscriptionTier.AUTOSYNC, SubscriptionTier.AUTOSYNC_LIVE}) {
            assertThat(VoiceQuota.plan(tier, "USER")).as(tier.name()).isEqualTo(VoiceQuota.Plan.PAID);
        }
        assertThat(VoiceQuota.plan(SubscriptionTier.SUPPORTER, "ADMIN")).isEqualTo(VoiceQuota.Plan.ADMIN);
        assertThat(VoiceQuota.plan(SubscriptionTier.NONE, "USER")).isEqualTo(VoiceQuota.Plan.FREE);
        assertThat(VoiceQuota.plan(null, "BETA_TESTER")).isEqualTo(VoiceQuota.Plan.FREE);
    }

    @Test
    void monthStartIsMidnightOfTheFirstInTheUsersZone() {
        assertThat(VoiceQuota.monthStart(NOW)).isEqualTo(ZonedDateTime.of(2026, 10, 1, 0, 0, 0, 0, ZoneId.of("Europe/Berlin")));
    }

    @Test
    void afterUseCountsDownTheVisibleLimit() {
        VoiceQuota q = VoiceQuota.of(VoiceQuota.Plan.FREE, 4, LocalDateTime.of(2026, 9, 2, 10, 0), NOW).afterUse();

        assertThat(q.remaining()).isZero();
        assertThat(q.exhausted()).isTrue();
        assertThat(VoiceQuota.of(VoiceQuota.Plan.ADMIN, 4, null, NOW).afterUse().remaining()).isNull();
    }
}
