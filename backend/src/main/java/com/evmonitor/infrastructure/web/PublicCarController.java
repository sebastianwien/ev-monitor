package com.evmonitor.infrastructure.web;

import com.evmonitor.application.CarImageService;
import com.evmonitor.application.CarShareService;
import com.evmonitor.application.PublicCarResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import com.evmonitor.infrastructure.image.PublicImageStyle.Lang;

/**
 * Oeffentlich geteilte Fahrzeugseiten. Liegt unter {@code /api/public/**} und ist
 * damit ueber die SecurityConfig freigegeben.
 *
 * Unbekannter Token, widerrufene Freigabe und fehlendes Bild liefern dasselbe 404:
 * ein Aufrufer soll nicht unterscheiden koennen, was davon zutrifft.
 */
@RestController
@RequestMapping("/api/public/car")
@RequiredArgsConstructor
public class PublicCarController {

    private final CarShareService shareService;
    private final CarImageService carImageService;
    private final com.evmonitor.infrastructure.image.SharedCarImageRenderer imageRenderer;
    private final com.evmonitor.infrastructure.image.SharedCarBannerRenderer bannerRenderer;
    private final com.evmonitor.infrastructure.image.SharedCurveImageCache imageCache;

    /** Kennzahlen aendern sich mit jeder Ladung - so lange darf ein Bild veraltet sein. */
    private static final Duration IMAGE_TTL = Duration.ofHours(1);

    /** Vorschaubild fuer Link-Karten. Laenger cachebar als das JSON, siehe PublicCurveController. */
    @GetMapping(value = "/{token}/og.png", produces = "image/png")
    public ResponseEntity<byte[]> getSharedCarOgImage(@PathVariable String token,
                                                      @RequestParam(required = false) String lang) {
        Lang l = Lang.of(lang);
        byte[] png = imageCache.get(langKey(CarShareService.imageCacheKey(token), l), IMAGE_TTL,
                k -> shareService.getPublicCar(token).map(c -> imageRenderer.render(c, l)).orElse(null));
        return pngOr404(png);
    }

    /**
     * Forum-Signatur-Banner (468x60). Wird unter jedem Forenbeitrag des Besitzers
     * geladen, deshalb serverseitig gecacht und mit langer Client-Cache-Zeit.
     */
    @GetMapping(value = "/{token}/banner.png", produces = "image/png")
    public ResponseEntity<byte[]> getSharedCarBanner(@PathVariable String token,
                                                     @RequestParam(required = false) String lang) {
        Lang l = Lang.of(lang);
        byte[] png = imageCache.get(langKey(CarShareService.bannerCacheKey(token), l), IMAGE_TTL,
                k -> shareService.getPublicCar(token).map(c -> bannerRenderer.render(c, l)).orElse(null));
        return pngOr404(png);
    }

    /** Ein Bild je Sprache; der Widerruf raeumt alle Varianten ueber das Schluessel-Praefix ab. */
    private static String langKey(String key, Lang lang) {
        return key + ":" + lang.code;
    }

    private static ResponseEntity<byte[]> pngOr404(byte[] png) {
        if (png == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.maxAge(IMAGE_TTL).cachePublic())
                .body(png);
    }

    @GetMapping("/{token}")
    public ResponseEntity<PublicCarResponse> getSharedCar(@PathVariable String token) {
        return shareService.getPublicCar(token)
                .map(body -> ResponseEntity.ok()
                        // Kurz genug, dass ein Widerruf zeitnah durchschlaegt.
                        .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                        .body(body))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Fahrzeugfoto - nur wenn der Besitzer es ohnehin oeffentlich gestellt hat. */
    @GetMapping(value = "/{token}/image", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<Resource> getSharedCarImage(@PathVariable String token) {
        return shareService.findSharedCarWithPublicImage(token)
                .flatMap(car -> carImageService.getImageResource(car.getId()))
                .map(res -> ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_JPEG)
                        .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                        .body(res))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
