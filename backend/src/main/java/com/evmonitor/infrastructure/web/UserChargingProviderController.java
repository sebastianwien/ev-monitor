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
    private final com.evmonitor.application.CoinLogService coinLogService;

    @GetMapping
    public List<UserChargingProviderResponse> getAll(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID userId = UUID.fromString(principal.getUser().getId().toString());
        return service.getAll(userId);
    }

    @PostMapping
    public org.springframework.http.ResponseEntity<UserChargingProviderResponse> add(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UserChargingProviderRequest request
    ) {
        UUID userId = UUID.fromString(principal.getUser().getId().toString());
        boolean firstCard = !coinLogService.hasEverReceivedCoinForAction(
                userId, com.evmonitor.application.CoinLogService.CoinEvent.CARD_CREATED.getDescription());
        UserChargingProviderResponse saved = service.add(userId, request);
        int coins = firstCard ? com.evmonitor.application.CoinLogService.CoinEvent.CARD_CREATED.getDefaultAmount() : 0;
        return org.springframework.http.ResponseEntity.ok()
                .header(EvLogController.COINS_AWARDED_HEADER, String.valueOf(coins))
                .body(saved);
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
