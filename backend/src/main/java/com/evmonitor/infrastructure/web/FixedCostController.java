package com.evmonitor.infrastructure.web;

import com.evmonitor.application.FixedCostRequest;
import com.evmonitor.application.FixedCostResponse;
import com.evmonitor.application.FixedCostService;
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
@RequestMapping("/api/fixed-costs")
@RequiredArgsConstructor
public class FixedCostController {

    private final FixedCostService fixedCostService;

    @GetMapping
    public List<FixedCostResponse> list(@RequestParam UUID carId, Authentication authentication) {
        UUID userId = userId(authentication);
        return fixedCostService.list(carId, userId);
    }

    @PostMapping
    public ResponseEntity<FixedCostResponse> create(
            @RequestParam UUID carId,
            @Valid @RequestBody FixedCostRequest request,
            Authentication authentication) {
        UUID userId = userId(authentication);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(fixedCostService.create(carId, userId, request));
    }

    @PutMapping("/{id}")
    public FixedCostResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody FixedCostRequest request,
            Authentication authentication) {
        UUID userId = userId(authentication);
        return fixedCostService.update(id, userId, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, Authentication authentication) {
        UUID userId = userId(authentication);
        fixedCostService.delete(id, userId);
        return ResponseEntity.noContent().build();
    }

    private UUID userId(Authentication authentication) {
        return ((UserPrincipal) authentication.getPrincipal()).getUser().getId();
    }
}
