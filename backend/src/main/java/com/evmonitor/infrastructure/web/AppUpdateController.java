package com.evmonitor.infrastructure.web;

import com.evmonitor.application.AppUpdateService;
import com.evmonitor.application.CapgoUpdateRequest;
import com.evmonitor.application.CapgoUpdateResponse;
import com.evmonitor.application.PublishBundleRequest;
import com.evmonitor.domain.AppBundle;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Capgo-Self-Hosted-Endpoints fuer Web-Bundle Live-Updates.
 *
 * Oeffentlich erreichbar (SecurityConfig: /api/app/**), da der Updater die App
 * schon vor dem Login abfragt.
 */
@RestController
@RequestMapping("/api/app")
public class AppUpdateController {

    /** Nur echte Semver-Versionen sind als Bundle-Pfad zugelassen (Path-Traversal-Schutz). */
    private static final Pattern SEMVER = Pattern.compile("\\d+\\.\\d+\\.\\d+");

    private final AppUpdateService updateService;
    private final Path bundleDir;
    private final String bundleBaseUrl;
    private final String publishToken;

    public AppUpdateController(
            AppUpdateService updateService,
            @Value("${app.bundle-dir:/opt/ev-monitor/app-bundles}") String bundleDir,
            @Value("${app.bundle-base-url:}") String bundleBaseUrl,
            @Value("${app.bundle-publish-token:}") String publishToken) {
        this.updateService = updateService;
        this.bundleDir = Path.of(bundleDir).normalize();
        this.bundleBaseUrl = bundleBaseUrl;
        this.publishToken = publishToken;
    }

    @PostMapping("/updates")
    public CapgoUpdateResponse checkForUpdate(@RequestBody(required = false) CapgoUpdateRequest request) {
        String currentVersion = request != null ? request.versionName() : null;
        return updateService.findUpdateFor(currentVersion)
                .map(bundle -> new CapgoUpdateResponse(
                        bundle.getVersion(),
                        bundleUrl(bundle.getVersion()),
                        bundle.getChecksum()))
                .orElseGet(CapgoUpdateResponse::none);
    }

    @GetMapping("/bundle/{version}.zip")
    public ResponseEntity<Resource> downloadBundle(@PathVariable String version) throws IOException {
        if (!SEMVER.matcher(version).matches()) {
            return ResponseEntity.badRequest().build();
        }
        AppBundle bundle = updateService.findByVersion(version).orElse(null);
        if (bundle == null) {
            return ResponseEntity.notFound().build();
        }
        Path file = bundleDir.resolve(bundle.getFilename()).normalize();
        if (!file.startsWith(bundleDir) || !Files.isRegularFile(file)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + bundle.getFilename() + "\"")
                .contentLength(Files.size(file))
                .body(new PathResource(file));
    }

    /**
     * Registriert ein von der CI hochgeladenes Bundle. Geschuetzt per Shared-Secret-Token
     * (Header X-App-Bundle-Token); ohne konfiguriertes Token ist der Endpoint deaktiviert
     * (fail-closed).
     */
    @PostMapping("/bundles")
    public ResponseEntity<?> publishBundle(
            @RequestHeader(value = "X-App-Bundle-Token", required = false) String token,
            @RequestBody(required = false) PublishBundleRequest request) {
        if (!isAuthorizedToPublish(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (request == null
                || request.version() == null || !SEMVER.matcher(request.version()).matches()
                || request.checksum() == null || request.checksum().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            AppBundle saved = updateService.publish(request.version(), request.checksum());
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("version", saved.getVersion()));
        } catch (IllegalStateException duplicate) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    private boolean isAuthorizedToPublish(String token) {
        if (publishToken == null || publishToken.isBlank() || token == null) {
            return false;
        }
        return MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8),
                publishToken.getBytes(StandardCharsets.UTF_8));
    }

    private String bundleUrl(String version) {
        String base = bundleBaseUrl;
        if (base == null || base.isBlank()) {
            base = ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path("/api/app/bundle")
                    .toUriString();
        }
        return base.replaceAll("/+$", "") + "/" + version + ".zip";
    }
}
