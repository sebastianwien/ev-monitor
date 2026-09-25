package com.evmonitor.infrastructure.web;

import com.evmonitor.application.ingest.IngestApiService;
import com.evmonitor.application.ingest.api.InternalIngestRequest;
import com.evmonitor.application.ingest.api.InternalIngestResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Vertrag B: Ladungen und Fahrten interner Dienste (Connectors) in einem Aufruf.
 * Gesichert über InternalAuthFilter (X-Internal-Token), nicht über den Nutzer-JWT.
 */
@RestController
@RequestMapping("/api/internal")
@RequiredArgsConstructor
public class InternalIngestController {

    private final IngestApiService ingestApiService;

    @PostMapping("/ingest")
    public ResponseEntity<InternalIngestResponse> ingest(@Valid @RequestBody InternalIngestRequest request) {
        return ResponseEntity.ok(ingestApiService.ingest(request));
    }
}
