package com.evmonitor.infrastructure.web;

import com.evmonitor.application.publicapi.ApiKeyCreatedResponse;
import com.evmonitor.application.publicapi.ApiKeyResponse;
import com.evmonitor.application.publicapi.ApiKeyService;
import com.evmonitor.infrastructure.security.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/user/api-keys")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @GetMapping
    public ResponseEntity<List<ApiKeyResponse>> listKeys(Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(apiKeyService.listKeys(principal.getUser().getId()));
    }

    @PostMapping
    public ResponseEntity<?> createKey(
            @Valid @RequestBody CreateApiKeyRequest body,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        try {
            ApiKeyCreatedResponse response = apiKeyService.createKey(principal.getUser().getId(), body.name());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteKey(@PathVariable UUID id, Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        apiKeyService.deleteKey(principal.getUser().getId(), id);
        return ResponseEntity.noContent().build();
    }

    public record CreateApiKeyRequest(
            @NotBlank @Size(max = 100) String name
    ) {}
}
