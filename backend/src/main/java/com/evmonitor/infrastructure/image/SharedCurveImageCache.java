package com.evmonitor.infrastructure.image;

import com.evmonitor.application.ShareRevokedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Fertige Vorschaubilder geteilter Seiten (Ladekurven, Fahrzeuge, Banner), nach Schluessel.
 *
 * Ohne Cache baut jeder Aufruf ein Bild neu auf - bis zu drei Megabyte Heap plus
 * PNG-Kodierung und beim Fahrzeug die volle Statistik, auf einem oeffentlichen
 * Endpunkt ohne Anmeldung. Das laesst sich billig ausloesen und teuer bedienen.
 *
 * <p>Zwei Arten von Eintraegen: Ladekurven sind unveraenderlich und leben bis zum
 * Widerruf. Fahrzeugbilder zeigen Kennzahlen, die sich mit jeder Ladung aendern -
 * sie bekommen eine Lebensdauer und werden danach beim naechsten Zugriff neu gebaut.
 *
 * <p>Gerendert wird ausserhalb des Map-Locks, aber pro Schluessel nur einmal:
 * wer denselben Schluessel gleichzeitig anfragt, wartet auf das laufende
 * Rendern statt es zu wiederholen. Ein langsames Fahrzeugbild (volle Statistik)
 * haelt so keine Ladekurven oder Banner anderer Nutzer auf.
 *
 * <p>Bewusst klein und mit harter Obergrenze: der Cache faengt Lastspitzen ab,
 * er haelt nicht alle je geteilten Bilder.
 */
@Component
public class SharedCurveImageCache {

    private static final int MAX_ENTRIES = 200;

    private record Entry(byte[] png, Instant expiresAt) {
        boolean expired(Instant now) {
            return expiresAt != null && !now.isBefore(expiresAt);
        }
    }

    private final Clock clock;
    /** Laufende Renderings, damit derselbe Schluessel nicht parallel gebaut wird. */
    private final ConcurrentHashMap<String, CompletableFuture<byte[]>> inFlight = new ConcurrentHashMap<>();
    private final Map<String, Entry> cache = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
                    return size() > MAX_ENTRIES;
                }
            });

    public SharedCurveImageCache() {
        this(Clock.systemUTC());
    }

    SharedCurveImageCache(Clock clock) {
        this.clock = clock;
    }

    /** Bild ohne Ablauf - lebt bis zum Widerruf oder bis es verdraengt wird. */
    public byte[] get(String key, Function<String, byte[]> renderer) {
        return get(key, null, renderer);
    }

    /**
     * Bild zum Schluessel, notfalls ueber {@code renderer} erzeugt und fuer {@code ttl}
     * behalten ({@code null} = unbegrenzt). Liefert der Renderer {@code null}
     * (unbekannter oder widerrufener Token), wird nichts abgelegt - sonst wuerde
     * ein Fehlschlag den Cache vergiften.
     */
    public byte[] get(String key, Duration ttl, Function<String, byte[]> renderer) {
        Entry hit = cache.get(key);
        if (hit != null && !hit.expired(clock.instant())) return hit.png();

        CompletableFuture<byte[]> mine = new CompletableFuture<>();
        CompletableFuture<byte[]> running = inFlight.putIfAbsent(key, mine);
        if (running != null) return running.join();
        try {
            byte[] png = renderer.apply(key);
            if (png == null) cache.remove(key);
            else cache.put(key, new Entry(png, ttl == null ? null : clock.instant().plus(ttl)));
            mine.complete(png);
            return png;
        } catch (RuntimeException e) {
            mine.completeExceptionally(e);
            throw e;
        } finally {
            inFlight.remove(key);
        }
    }

    /**
     * Wirft das Bild eines zurueckgezogenen Shares sofort weg.
     *
     * Ohne das wuerde das Bild nach dem Widerruf weiter ausgeliefert, bis es
     * verdraengt wird oder ablaeuft - waehrend die Seite selbst schon tot ist.
     */
    @EventListener
    public void onShareRevoked(ShareRevokedEvent event) {
        String prefix = event.token() + ":";
        synchronized (cache) {
            cache.keySet().removeIf(k -> k.equals(event.token()) || k.startsWith(prefix));
        }
    }
}
