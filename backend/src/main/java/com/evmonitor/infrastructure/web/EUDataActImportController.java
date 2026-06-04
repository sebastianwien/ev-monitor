package com.evmonitor.infrastructure.web;

import com.evmonitor.application.imports.eudataact.EUDataActImportService;
import com.evmonitor.application.imports.eudataact.EUDataActPreviewResult;
import com.evmonitor.application.publicapi.ImportApiResult;
import com.evmonitor.infrastructure.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/import/eu-data-act")
@Slf4j
@RequiredArgsConstructor
public class EUDataActImportController {

    private static final long MAX_UPLOAD_BYTES = 5 * 1024 * 1024L; // 5 MB

    private final EUDataActImportService importService;

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> preview(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestPart("file") MultipartFile file
    ) {
        try {
            validateUpload(file);
            EUDataActPreviewResult result = importService.preview(
                    file.getInputStream(), file.getOriginalFilename());
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.warn("EU Data Act preview failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("error", "Datei konnte nicht verarbeitet werden. Bitte prüfe das Format."));
        }
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> importData(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestPart("file") MultipartFile file,
            @RequestParam("carId") UUID carId
    ) {
        try {
            validateUpload(file);
            ImportApiResult result = importService.importData(
                    principal.getUser().getId(), carId,
                    file.getInputStream(), file.getOriginalFilename());
            return ResponseEntity.ok(result);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("EU Data Act import failed for user {} car {}", principal.getUser().getId(), carId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Import fehlgeschlagen. Bitte versuche es erneut."));
        }
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Keine Datei hochgeladen");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new IllegalArgumentException("Datei zu groß (max. 5 MB)");
        }
        String name = file.getOriginalFilename();
        if (name != null && !name.toLowerCase().endsWith(".json") && !name.toLowerCase().endsWith(".zip")) {
            throw new IllegalArgumentException("Nur JSON- oder ZIP-Dateien werden akzeptiert");
        }
    }
}
