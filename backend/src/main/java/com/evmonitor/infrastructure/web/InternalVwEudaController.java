package com.evmonitor.infrastructure.web;

import com.evmonitor.application.imports.vweuda.VwEudaImportService;
import com.evmonitor.application.imports.vweuda.VwEudaUnreadableException;
import com.evmonitor.application.imports.vweuda.VwEudaAutoSyncEntitlementService;
import com.evmonitor.application.imports.vweuda.VwEudaNotificationService;
import com.evmonitor.domain.UserRepository;
import com.evmonitor.application.publicapi.ImportApiResult;
import com.evmonitor.domain.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * Endpunkte fuer den Connectors-Service (EU-Data-Act-AutoSync). Gesichert durch
 * InternalAuthFilter (X-Internal-Token), nicht durch Nutzer-JWT. Der Connector holt die
 * Portal-Datensaetze ab; parsen und importieren tut ausschliesslich der Core - mit demselben
 * Parser wie der manuelle Upload.
 */
@RestController
@RequestMapping("/api/internal/eu-data-act")
@RequiredArgsConstructor
@Slf4j
public class InternalVwEudaController {

    private final VwEudaImportService importService;
    private final VwEudaNotificationService notifications;
    private final VwEudaAutoSyncEntitlementService entitlement;
    private final UserRepository userRepository;

    public record ImportResponse(int imported, int skipped, int errors) {}

    /** Einmaliger Historien-Export - alles andere ist der laufende 15-Minuten-Feed. */
    private static final String KIND_HISTORY = "HISTORY";

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportResponse> importDataset(@RequestPart("userId") String userId,
                                                        @RequestPart("carId") String carId,
                                                        @RequestPart(value = "kind", required = false) String kind,
                                                        @RequestPart("file") MultipartFile file) throws IOException {
        // Der laufende Feed liefert auch bei stehendem Fahrzeug Telemetrie ohne Ladedaten - das
        // ist kein Fehler. Die Historie kommt nur einmal und wird deshalb strikt geparst.
        boolean lenient = !KIND_HISTORY.equalsIgnoreCase(kind);
        long started = System.nanoTime();
        ImportApiResult result = importService.importData(UUID.fromString(userId), UUID.fromString(carId),
                file, file.getOriginalFilename(), DataSource.EU_DATA_ACT_SYNC, lenient);
        // Dauer ist die Messgroesse fuer den Connector-Timeout (Historien-Exporte brauchen Minuten).
        log.info("[EUDA] Import {} kind={} {} B carId={}: {} importiert, {} uebersprungen, {} Fehler in {} ms",
                file.getOriginalFilename(), lenient ? "CONTINUOUS" : "HISTORY", file.getSize(), carId,
                result.imported(), result.skipped(), result.errors(), (System.nanoTime() - started) / 1_000_000);
        return ResponseEntity.ok(new ImportResponse(result.imported(), result.skipped(), result.errors()));
    }

    /**
     * Nur eine unlesbare Datei ergibt 422 - daran erkennt der Connector, dass ein erneuter
     * Versuch sinnlos ist. Fachliche Fehler wie ein unbekanntes Fahrzeug bleiben 400 und
     * sollen sichtbar scheitern, statt den Datensatz still verschwinden zu lassen.
     */
    @ExceptionHandler(VwEudaUnreadableException.class)
    public ResponseEntity<Map<String, String>> handleUnreadable(VwEudaUnreadableException e) {
        log.info("[EUDA] Datensatz nicht verarbeitbar: {}", e.getMessage());
        return ResponseEntity.unprocessableEntity().body(Map.of("message", String.valueOf(e.getMessage())));
    }

    /** Darf der Nutzer AutoSync (noch) nutzen - der Connector fragt beim Verbinden und taeglich. */
    @GetMapping("/entitlement/{userId}")
    public ResponseEntity<VwEudaAutoSyncEntitlementService.Entitlement> entitlement(@PathVariable UUID userId) {
        return userRepository.findById(userId)
                .map(u -> ResponseEntity.ok(entitlement.entitlementFor(u)))
                .orElse(ResponseEntity.notFound().build());
    }

    public record NotifyRequest(UUID userId, UUID carId, String event, Map<String, Object> params) {}

    @PostMapping("/notify")
    public ResponseEntity<Void> notify(@RequestBody NotifyRequest request) {
        notifications.notify(request.userId(), request.event(), request.params() == null ? Map.of() : request.params());
        return ResponseEntity.noContent().build();
    }
}
