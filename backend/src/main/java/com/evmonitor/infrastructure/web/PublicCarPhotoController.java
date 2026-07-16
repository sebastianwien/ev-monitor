package com.evmonitor.infrastructure.web;

import com.evmonitor.application.CarImageService;
import com.evmonitor.application.ModelPhotoSummary;
import com.evmonitor.application.PublicCarPhotoService;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Public, unauthenticated API for user-shared car photos, grouped by model.
 * Backs the photo gallery on the model list page ({@code /modelle}).
 *
 * <p>Only photos the owner explicitly shared ({@code image_public = true}) are served -
 * enforced both by the repository query (list endpoints) and an ownership-independent
 * public-flag check (image endpoint). No user-identifiable data is returned.
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
@Slf4j
public class PublicCarPhotoController {

    private static final int THUMB_PX = 160;
    private static final int HERO_PX = 400;

    private static final CacheControl LIST_CACHE = CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic();
    // Images are near-immutable per (carId, size); re-uploads become visible within a day.
    private static final CacheControl IMAGE_CACHE = CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic();

    private final PublicCarPhotoService photoService;
    private final CarImageService carImageService;
    private final CarRepository carRepository;

    /** GET /api/public/model-photos/summary - hero + count per model that has a public photo. */
    @GetMapping("/model-photos/summary")
    public ResponseEntity<List<ModelPhotoSummary>> getPhotoSummary() {
        return ResponseEntity.ok()
                .cacheControl(LIST_CACHE)
                .body(photoService.getPhotoSummaries());
    }

    /** GET /api/public/model-photos/{model} - all public photo car ids for a model, newest first. */
    @GetMapping("/model-photos/{model}")
    public ResponseEntity<List<java.util.UUID>> getModelPhotos(@PathVariable String model) {
        return photoService.getPublicPhotoCarIds(model)
                .map(ids -> ResponseEntity.ok().cacheControl(LIST_CACHE).body(ids))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * GET /api/public/car-photos/{carId}?size=thumb|hero - downscaled JPEG for a single car photo.
     * Serves only public images; private/unknown cars return 404.
     */
    @GetMapping("/car-photos/{carId}")
    public ResponseEntity<byte[]> getCarPhoto(@PathVariable java.util.UUID carId,
                                              @RequestParam(defaultValue = "hero") String size) {
        Car car = carRepository.findById(carId).orElse(null);
        if (car == null || !car.isImagePublic() || car.getImagePath() == null) {
            return ResponseEntity.notFound().build();
        }

        int maxDim = "thumb".equalsIgnoreCase(size) ? THUMB_PX : HERO_PX;
        String etag = "\"" + carId + "-" + (maxDim == THUMB_PX ? "t" : "h") + "-"
                + (car.getUpdatedAt() == null ? 0L : car.getUpdatedAt().toEpochSecond(ZoneOffset.UTC)) + "\"";

        try {
            return carImageService.getScaledJpeg(carId, maxDim)
                    .map(bytes -> ResponseEntity.ok()
                            .cacheControl(IMAGE_CACHE)
                            .eTag(etag)
                            .contentType(MediaType.IMAGE_JPEG)
                            .body(bytes))
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (IOException e) {
            log.warn("Failed to scale public car photo {}: {}", carId, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
}
