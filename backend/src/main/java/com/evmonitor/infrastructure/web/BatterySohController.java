package com.evmonitor.infrastructure.web;

import com.evmonitor.application.BatterySohRequest;
import com.evmonitor.application.BatterySohResponse;
import com.evmonitor.application.BatterySohService;
import com.evmonitor.infrastructure.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/cars/{carId}/soh")
@RequiredArgsConstructor
public class BatterySohController {

    private final BatterySohService sohService;

    @GetMapping
    public ResponseEntity<List<BatterySohResponse>> getHistory(
            @PathVariable UUID carId,
            Authentication authentication) {
        UUID userId = ((UserPrincipal) authentication.getPrincipal()).getUser().getId();
        return ResponseEntity.ok(sohService.getHistory(carId, userId));
    }

    @PostMapping
    public ResponseEntity<BatterySohResponse> addMeasurement(
            @PathVariable UUID carId,
            @Valid @RequestBody BatterySohRequest request,
            Authentication authentication) {
        UUID userId = ((UserPrincipal) authentication.getPrincipal()).getUser().getId();
        return ResponseEntity.status(HttpStatus.CREATED).body(sohService.addMeasurement(carId, userId, request));
    }

    @PutMapping("/{entryId}")
    public ResponseEntity<BatterySohResponse> updateMeasurement(
            @PathVariable UUID carId,
            @PathVariable UUID entryId,
            @Valid @RequestBody BatterySohRequest request,
            Authentication authentication) {
        UUID userId = ((UserPrincipal) authentication.getPrincipal()).getUser().getId();
        return ResponseEntity.ok(sohService.updateLatest(entryId, carId, userId, request));
    }

    @DeleteMapping("/{entryId}")
    public ResponseEntity<Void> deleteMeasurement(
            @PathVariable UUID carId,
            @PathVariable UUID entryId,
            Authentication authentication) {
        UUID userId = ((UserPrincipal) authentication.getPrincipal()).getUser().getId();
        sohService.deleteMeasurement(entryId, carId, userId);
        return ResponseEntity.noContent().build();
    }
}
