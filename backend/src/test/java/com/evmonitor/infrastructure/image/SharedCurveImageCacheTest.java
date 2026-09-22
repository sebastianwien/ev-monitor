package com.evmonitor.infrastructure.image;

import com.evmonitor.application.ShareRevokedEvent;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bild-Cache geteilter Seiten: Eintraege ohne Ablauf leben bis zum Widerruf,
 * Eintraege mit TTL werden nach Ablauf neu gerendert.
 */
class SharedCurveImageCacheTest {

    private static final byte[] PNG = {1, 2, 3};

    /** Uhr, die der Test vorstellen kann. */
    private static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-09-22T10:00:00Z");
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @Test
    void entryWithoutTtl_isRenderedOnce() {
        MutableClock clock = new MutableClock();
        SharedCurveImageCache cache = new SharedCurveImageCache(clock);
        AtomicInteger renders = new AtomicInteger();

        cache.get("a", k -> { renders.incrementAndGet(); return PNG; });
        clock.now = clock.now.plus(Duration.ofDays(30));
        cache.get("a", k -> { renders.incrementAndGet(); return PNG; });

        assertEquals(1, renders.get());
    }

    @Test
    void entryWithTtl_isRenderedAgainAfterExpiry() {
        MutableClock clock = new MutableClock();
        SharedCurveImageCache cache = new SharedCurveImageCache(clock);
        AtomicInteger renders = new AtomicInteger();

        cache.get("a", Duration.ofHours(1), k -> { renders.incrementAndGet(); return PNG; });
        clock.now = clock.now.plus(Duration.ofMinutes(59));
        cache.get("a", Duration.ofHours(1), k -> { renders.incrementAndGet(); return PNG; });
        assertEquals(1, renders.get(), "vor Ablauf kein zweites Rendern");

        clock.now = clock.now.plus(Duration.ofMinutes(2));
        cache.get("a", Duration.ofHours(1), k -> { renders.incrementAndGet(); return PNG; });
        assertEquals(2, renders.get(), "nach Ablauf neu rendern");
    }

    @Test
    void nullResult_isNotCached() {
        SharedCurveImageCache cache = new SharedCurveImageCache(Clock.systemUTC());
        AtomicInteger renders = new AtomicInteger();

        assertNull(cache.get("a", Duration.ofHours(1), k -> { renders.incrementAndGet(); return null; }));
        cache.get("a", Duration.ofHours(1), k -> { renders.incrementAndGet(); return null; });

        assertEquals(2, renders.get());
    }

    @Test
    void revoke_dropsEntryImmediately() {
        SharedCurveImageCache cache = new SharedCurveImageCache(Clock.systemUTC());
        AtomicInteger renders = new AtomicInteger();
        cache.get("a", Duration.ofHours(1), k -> { renders.incrementAndGet(); return PNG; });

        cache.onShareRevoked(new ShareRevokedEvent("a"));
        cache.get("a", Duration.ofHours(1), k -> { renders.incrementAndGet(); return PNG; });

        assertEquals(2, renders.get());
    }
}
