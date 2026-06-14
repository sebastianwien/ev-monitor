package com.evmonitor.infrastructure.web;

import com.evmonitor.application.AppVersionResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Liefert Versions-Metadaten fuer die native App (Capacitor).
 *
 * Oeffentlich erreichbar (siehe SecurityConfig: /api/app/**), da die App die
 * Version schon vor dem Login prueft. Es werden ausschliesslich nicht-sensible
 * Build-Metadaten ausgegeben.
 */
@RestController
@RequestMapping("/api/app")
public class AppVersionController {

    private final String webBuildHash;
    private final int minNativeVersion;

    public AppVersionController(
            @Value("${app.web-build-hash:dev}") String webBuildHash,
            @Value("${app.min-native-version:1}") int minNativeVersion) {
        this.webBuildHash = webBuildHash;
        this.minNativeVersion = minNativeVersion;
    }

    @GetMapping("/version")
    public ResponseEntity<AppVersionResponse> getVersion() {
        return ResponseEntity.ok(new AppVersionResponse(webBuildHash, minNativeVersion));
    }
}
