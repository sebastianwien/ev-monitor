package com.evmonitor.infrastructure.web;

import com.evmonitor.application.imports.eudataact.EUDataActImportService;
import com.evmonitor.application.imports.eudataact.EudaNotificationService;
import com.evmonitor.application.publicapi.ImportApiResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
public class InternalEuDataActController {

    private final EUDataActImportService importService;
    private final EudaNotificationService notifications;

    public record ImportResponse(int imported, int skipped, int errors) {}

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportResponse> importDataset(@RequestPart("userId") String userId,
                                                        @RequestPart("carId") String carId,
                                                        @RequestPart("file") MultipartFile file) throws IOException {
        ImportApiResult result = importService.importData(UUID.fromString(userId), UUID.fromString(carId),
                file, file.getOriginalFilename());
        return ResponseEntity.ok(new ImportResponse(result.imported(), result.skipped(), result.errors()));
    }

    public record NotifyRequest(UUID userId, UUID carId, String event, Map<String, Object> params) {}

    @PostMapping("/notify")
    public ResponseEntity<Void> notify(@RequestBody NotifyRequest request) {
        notifications.notify(request.userId(), request.event(), request.params() == null ? Map.of() : request.params());
        return ResponseEntity.noContent().build();
    }
}
