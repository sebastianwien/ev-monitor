package com.evmonitor.infrastructure.web;

import com.evmonitor.application.EvLogShareService;
import com.evmonitor.application.PublicCurveResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Oeffentlich geteilte Ladekurven - der einzige unauthentifizierte Zugang zu
 * Ladedaten. Liegt unter {@code /api/public/**} und ist damit ueber die
 * SecurityConfig freigegeben.
 *
 * Fuer jede Art von Fehlschlag - unbekannter Token, widerrufene Freigabe,
 * kaputte Kurve - kommt dasselbe 404 zurueck. Ein Aufrufer soll nicht
 * unterscheiden koennen, ob ein Token nie existiert hat oder zurueckgezogen wurde.
 */
@RestController
@RequestMapping("/api/public/curve")
@RequiredArgsConstructor
public class PublicCurveController {

    private final EvLogShareService shareService;
    private final com.evmonitor.infrastructure.image.PowerCurveImageRenderer imageRenderer;

    /**
     * Vorschaubild fuer Link-Karten in sozialen Netzen und Messengern.
     *
     * Laenger cachebar als die JSON-Antwort: Crawler holen es oft mehrfach, und
     * ein Widerruf nimmt zuerst die Seite offline - ein noch kurz im CDN
     * liegendes Bild verraet ohne den zugehoerigen Kontext nichts Zusaetzliches.
     */
    @GetMapping(value = "/{token}/og.png", produces = "image/png")
    public ResponseEntity<byte[]> getSharedCurveImage(@PathVariable String token) {
        byte[] png = shareService.getPublicCurve(token).map(imageRenderer::render).orElse(null);
        if (png == null) return ResponseEntity.notFound().build();

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .body(png);
    }

    @GetMapping("/{token}")
    public ResponseEntity<PublicCurveResponse> getSharedCurve(@PathVariable String token) {
        return shareService.getPublicCurve(token)
                .map(body -> ResponseEntity.ok()
                        // Oeffentlich cachebar: der Inhalt haengt an keinem Nutzer.
                        // Kurz genug, dass ein Widerruf zeitnah durchschlaegt.
                        .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                        .body(body))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
