package com.evmonitor.infrastructure.web;

import com.evmonitor.application.publicapi.CpoNameNormalizer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Public API", description = "External upload API for charging sessions (Wallboxen, Skripte, Home-Automation)")
@RequiredArgsConstructor
public class ChargingProviderController {

    private final CpoNameNormalizer cpoNameNormalizer;

    @GetMapping("/charging-providers")
    @Operation(
            summary = "List known charging providers",
            description = """
                    Returns the canonical list of known CPO (Charge Point Operator) names.

                    Use these values for the `cpo_name` field when uploading sessions via `POST /api/v1/sessions`.
                    Unknown values are still accepted and stored as-is, but canonical names ensure correct aggregation.

                    No authentication required.
                    """
    )
    public ResponseEntity<List<String>> getChargingProviders() {
        return ResponseEntity.ok(cpoNameNormalizer.getKnownCpos());
    }
}
