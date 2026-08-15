package com.evmonitor.infrastructure.image;

import com.evmonitor.application.ShareRevokedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Fertige Vorschaubilder geteilter Ladekurven, nach Token.
 *
 * Ohne Cache baut jeder Aufruf ein 1200x630-Bild neu auf - rund drei Megabyte
 * Heap plus PNG-Kodierung, auf einem oeffentlichen Endpunkt ohne Anmeldung. Das
 * laesst sich billig ausloesen und teuer bedienen. Der Inhalt haengt nur am
 * Token und ist unveraenderlich, taugt also uneingeschraenkt zum Cachen.
 *
 * <p>Die Berechnung laeuft unter dem Map-Lock: gleichzeitige Anfragen
 * serialisieren sich damit, statt parallel je drei Megabyte zu belegen. Genau
 * das ist hier erwuenscht - der Endpunkt wird von Crawlern in Schueben
 * abgerufen, nicht von vielen Nutzern gleichzeitig.
 *
 * <p>Bewusst klein und mit harter Obergrenze: der Cache faengt Lastspitzen ab,
 * er haelt nicht alle je geteilten Kurven.
 */
@Component
public class SharedCurveImageCache {

    private static final int MAX_ENTRIES = 200;

    private final Map<String, byte[]> cache = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
                    return size() > MAX_ENTRIES;
                }
            });

    /**
     * Bild zum Token, notfalls ueber {@code renderer} erzeugt. Liefert der
     * Renderer {@code null} (unbekannter oder widerrufener Token), wird nichts
     * abgelegt - sonst wuerde ein Fehlschlag den Cache vergiften.
     */
    public byte[] get(String token, Function<String, byte[]> renderer) {
        return cache.computeIfAbsent(token, renderer);
    }

    /**
     * Wirft das Bild eines zurueckgezogenen Shares sofort weg.
     *
     * Ohne das wuerde das Vorschaubild nach dem Widerruf weiter ausgeliefert,
     * bis es verdraengt wird - waehrend die Seite selbst schon tot ist.
     */
    @EventListener
    public void onShareRevoked(ShareRevokedEvent event) {
        cache.remove(event.token());
    }
}
