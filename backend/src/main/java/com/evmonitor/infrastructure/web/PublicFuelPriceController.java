package com.evmonitor.infrastructure.web;

import com.evmonitor.application.FuelPriceResponse;
import com.evmonitor.infrastructure.external.FuelPriceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/**
 * Bundesweite Spritpreise (Tankerkoenig, taeglich aktualisiert) fuer Benziner-Vergleiche
 * im Frontend. Keine Nutzerdaten, daher oeffentlich und eine Stunde cachebar.
 */
@RestController
@RequestMapping("/api/public/fuel-prices")
@RequiredArgsConstructor
public class PublicFuelPriceController {

    private static final CacheControl PUBLIC_1H = CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic();

    private final FuelPriceService fuelPriceService;

    @GetMapping
    public ResponseEntity<FuelPriceResponse> get() {
        return ResponseEntity.ok().cacheControl(PUBLIC_1H).body(fuelPriceService.snapshot());
    }
}
