package com.evmonitor.application.voice;

import com.evmonitor.domain.SubscriptionTier;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** Deckel laut Plan: Free 5 je Monat, erster Monat ab erster Aufnahme 10, bezahlte Tiers still 60. */
class VoiceQuotaTest {

    private static final ZonedDateTime NOW = ZonedDateTime.of(2026, 10, 3, 15, 0, 0, 0, ZoneId.of("Europe/Berlin"));

    @Test
    void freeUserBeforeFirstRecordingIsInTheIntroMonth() {
        VoiceQuota q = VoiceQuota.of(false, 0, null, NOW);

        assertThat(q.limit()).isEqualTo(10);
        assertThat(q.remaining()).isEqualTo(10);
        assertThat(q.exhausted()).isFalse();
    }

    @Test
    void freeUserWithinFirstMonthAfterFirstRecordingGetsTen() {
        VoiceQuota q = VoiceQuota.of(false, 7, LocalDateTime.of(2026, 9, 20, 10, 0), NOW);

        assertThat(q.limit()).isEqualTo(10);
        assertThat(q.remaining()).isEqualTo(3);
    }

    @Test
    void freeUserAfterTheIntroMonthGetsFive() {
        VoiceQuota q = VoiceQuota.of(false, 5, LocalDateTime.of(2026, 9, 2, 10, 0), NOW);

        assertThat(q.limit()).isEqualTo(5);
        assertThat(q.remaining()).isZero();
        assertThat(q.exhausted()).isTrue();
    }

    @Test
    void resetsOnTheFirstOfNextMonthInTheUsersZone() {
        assertThat(VoiceQuota.of(false, 0, null, NOW).resetsOn()).isEqualTo(LocalDate.of(2026, 11, 1));
    }

    @Test
    void fairUseOnlyHasNoVisibleLimitButASilentCap() {
        VoiceQuota open = VoiceQuota.of(true, 59, LocalDateTime.of(2026, 1, 1, 0, 0), NOW);
        VoiceQuota capped = VoiceQuota.of(true, 60, LocalDateTime.of(2026, 1, 1, 0, 0), NOW);

        assertThat(open.limit()).isNull();
        assertThat(open.remaining()).isNull();
        assertThat(open.exhausted()).isFalse();
        assertThat(capped.exhausted()).isTrue();
    }

    @Test
    void paidTiersAndAdminsGetFairUseOnly() {
        for (SubscriptionTier tier : new SubscriptionTier[]{SubscriptionTier.SUPPORTER, SubscriptionTier.AUTOSYNC, SubscriptionTier.AUTOSYNC_LIVE}) {
            assertThat(VoiceQuota.fairUseOnly(tier, "USER")).as(tier.name()).isTrue();
        }
        assertThat(VoiceQuota.fairUseOnly(SubscriptionTier.NONE, "ADMIN")).isTrue();
        assertThat(VoiceQuota.fairUseOnly(SubscriptionTier.NONE, "USER")).isFalse();
        assertThat(VoiceQuota.fairUseOnly(SubscriptionTier.NONE, "BETA_TESTER")).isFalse();
    }

    @Test
    void monthStartIsMidnightOfTheFirstInTheUsersZone() {
        assertThat(VoiceQuota.monthStart(NOW)).isEqualTo(ZonedDateTime.of(2026, 10, 1, 0, 0, 0, 0, ZoneId.of("Europe/Berlin")));
    }

    @Test
    void afterUseCountsDownTheVisibleLimit() {
        VoiceQuota q = VoiceQuota.of(false, 4, LocalDateTime.of(2026, 9, 2, 10, 0), NOW).afterUse();

        assertThat(q.remaining()).isZero();
        assertThat(q.exhausted()).isTrue();
        assertThat(VoiceQuota.of(true, 4, null, NOW).afterUse().remaining()).isNull();
    }
}
