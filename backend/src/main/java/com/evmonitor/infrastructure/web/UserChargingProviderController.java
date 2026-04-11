package com.evmonitor.infrastructure.web;

import com.evmonitor.application.user.UserChargingProviderRequest;
import com.evmonitor.application.user.UserChargingProviderResponse;
import com.evmonitor.application.user.UserChargingProviderService;
import com.evmonitor.infrastructure.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users/me/charging-providers")
@RequiredArgsConstructor
public class UserChargingProviderController {

    private final UserChargingProviderService service;

    @GetMapping
    public List<UserChargingProviderResponse> getAll(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID userId = UUID.fromString(principal.getUser().getId().toString());
        return service.getAll(userId);
    }

    @PostMapping
    public UserChargingProviderResponse add(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UserChargingProviderRequest request
    ) {
        UUID userId = UUID.fromString(principal.getUser().getId().toString());
        return service.add(userId, request);
    }

    @PutMapping("/{id}")
    public UserChargingProviderResponse update(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UserChargingProviderRequest request
    ) {
        UUID userId = UUID.fromString(principal.getUser().getId().toString());
        return service.update(userId, id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        UUID userId = UUID.fromString(principal.getUser().getId().toString());
        service.delete(userId, id);
        return ResponseEntity.noContent().build();
    }
}
