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
 * <p>Die Berechnung laeuft unter dem Map-Lock: gleichzeitige Anfragen
 * serialisieren sich damit, statt parallel je drei Megabyte zu belegen. Genau
 * das ist hier erwuenscht - die Endpunkte werden von Crawlern und Foren in
 * Schueben abgerufen, nicht von vielen Nutzern gleichzeitig.
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
        Instant now = clock.instant();
        synchronized (cache) {
            Entry hit = cache.get(key);
            if (hit != null && !hit.expired(now)) return hit.png();
            byte[] png = renderer.apply(key);
            if (png == null) {
                cache.remove(key);
                return null;
            }
            cache.put(key, new Entry(png, ttl == null ? null : now.plus(ttl)));
            return png;
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
        cache.remove(event.token());
    }
}
