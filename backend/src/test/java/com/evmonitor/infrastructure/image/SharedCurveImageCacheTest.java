package com.evmonitor.infrastructure.image;

import com.evmonitor.application.ShareRevokedEvent;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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

    @Test
    void revoke_dropsAllLanguageVariantsOfTheKey() {
        SharedCurveImageCache cache = new SharedCurveImageCache(Clock.systemUTC());
        AtomicInteger renders = new AtomicInteger();
        cache.get("car-img:tok:de", Duration.ofHours(1), k -> { renders.incrementAndGet(); return PNG; });
        cache.get("car-img:tok:en", Duration.ofHours(1), k -> { renders.incrementAndGet(); return PNG; });
        cache.get("car-img:token2:de", Duration.ofHours(1), k -> { renders.incrementAndGet(); return PNG; });

        cache.onShareRevoked(new ShareRevokedEvent("car-img:tok"));

        cache.get("car-img:tok:de", Duration.ofHours(1), k -> { renders.incrementAndGet(); return PNG; });
        cache.get("car-img:tok:en", Duration.ofHours(1), k -> { renders.incrementAndGet(); return PNG; });
        cache.get("car-img:token2:de", Duration.ofHours(1), k -> { renders.incrementAndGet(); return PNG; });
        assertEquals(5, renders.get(), "beide Sprachvarianten weg, anderer Token unberuehrt");
    }

    @Test
    void slowRenderOfOneKey_doesNotBlockOtherKeys() throws Exception {
        SharedCurveImageCache cache = new SharedCurveImageCache(Clock.systemUTC());
        CountDownLatch slowStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<byte[]> slow = pool.submit(() -> cache.get("slow", Duration.ofHours(1), k -> {
                slowStarted.countDown();
                try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                return PNG;
            }));
            assertTrue(slowStarted.await(2, TimeUnit.SECONDS));

            Future<byte[]> fast = pool.submit(() -> cache.get("fast", Duration.ofHours(1), k -> PNG));
            assertArrayEquals(PNG, fast.get(2, TimeUnit.SECONDS), "anderer Schluessel wartet nicht auf das langsame Rendern");

            release.countDown();
            assertArrayEquals(PNG, slow.get(2, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentRequestsForSameKey_renderOnce() throws Exception {
        SharedCurveImageCache cache = new SharedCurveImageCache(Clock.systemUTC());
        AtomicInteger renders = new AtomicInteger();
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            java.util.List<Future<byte[]>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < 4; i++) {
                futures.add(pool.submit(() -> cache.get("a", Duration.ofHours(1), k -> {
                    renders.incrementAndGet();
                    try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    return PNG;
                })));
            }
            Thread.sleep(200);
            release.countDown();
            for (Future<byte[]> f : futures) assertArrayEquals(PNG, f.get(2, TimeUnit.SECONDS));
            assertEquals(1, renders.get(), "gleicher Schluessel wird nur einmal gerendert");
        } finally {
            pool.shutdownNow();
        }
    }
}
