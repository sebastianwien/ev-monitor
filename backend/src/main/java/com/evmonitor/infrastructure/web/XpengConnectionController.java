package com.evmonitor.infrastructure.web;

import com.evmonitor.domain.xpeng.VinUtils;
import com.evmonitor.infrastructure.persistence.xpeng.XpengConnection;
import com.evmonitor.application.imports.xpeng.XpengConnectionService;
import com.evmonitor.infrastructure.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/imports/xpeng/connections")
@Slf4j
@RequiredArgsConstructor
public class XpengConnectionController {

    private final XpengConnectionService service;

    @GetMapping
    public ResponseEntity<List<ConnectionDto>> list(@AuthenticationPrincipal UserPrincipal principal) {
        UUID userId = principal.getUser().getId();
        List<ConnectionDto> dtos = service.getActiveConnectionsForUser(userId).stream()
                .map(this::toDto)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @PostMapping
    public ResponseEntity<?> grant(@AuthenticationPrincipal UserPrincipal principal,
                                   @RequestBody GrantRequest req,
                                   HttpServletRequest http) {
        if (req.consentAccepted() == null || !req.consentAccepted()) {
            return ResponseEntity.badRequest().body(Map.of("error", "consentAccepted muss true sein"));
        }
        try {
            UUID userId = principal.getUser().getId();
            String ip = WebUtils.clientIp(http);
            String userAgent = truncate(http.getHeader("User-Agent"), 500);
            boolean autoSync = Boolean.TRUE.equals(req.autoSync());
            XpengConnection conn = service.grantConsent(
                    userId, req.carId(), normalizeVin(req.vin()), ip, userAgent, autoSync, req.xpengEmail());
            return ResponseEntity.ok(toDto(conn));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/{connectionId}/autosync")
    public ResponseEntity<?> activateAutoSync(@AuthenticationPrincipal UserPrincipal principal,
                                              @PathVariable UUID connectionId,
                                              @RequestBody AutoSyncRequest req,
                                              HttpServletRequest http) {
        if (req.consentAccepted() == null || !req.consentAccepted()) {
            return ResponseEntity.badRequest().body(Map.of("error", "consentAccepted muss true sein"));
        }
        try {
            UUID userId = principal.getUser().getId();
            XpengConnection conn = service.activateAutoSync(
                    userId, connectionId, req.xpengEmail(),
                    WebUtils.clientIp(http), truncate(http.getHeader("User-Agent"), 500));
            return ResponseEntity.ok(toDto(conn));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/{connectionId}/email")
    public ResponseEntity<?> updateEmail(@AuthenticationPrincipal UserPrincipal principal,
                                         @PathVariable UUID connectionId,
                                         @RequestBody EmailRequest req) {
        try {
            XpengConnection conn = service.updateXpengEmail(
                    principal.getUser().getId(), connectionId, req.xpengEmail());
            return ResponseEntity.ok(toDto(conn));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{connectionId}")
    public ResponseEntity<?> revoke(@AuthenticationPrincipal UserPrincipal principal,
                                    @PathVariable UUID connectionId) {
        try {
            service.revokeConsent(principal.getUser().getId(), connectionId);
            return ResponseEntity.noContent().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    private ConnectionDto toDto(XpengConnection c) {
        return new ConnectionDto(
                c.getId(), c.getCarId(), c.getVin(), VinUtils.mask(c.getVin()),
                c.getConsentGrantedAt(), c.getConsentVersion(),
                c.getLastSuccessfulImportAt(), c.getTotalImportsCount(),
                c.isAutoSyncEnabled(), c.getLastRequestSentAt(),
                maskEmail(c.getXpengEmail()));
    }

    public record EmailRequest(
            @NotNull @Email @Size(max = 255) String xpengEmail) {}

    public record AutoSyncRequest(
            @NotNull Boolean consentAccepted,
            @Email @Size(max = 255) String xpengEmail) {}

    public record GrantRequest(
            @NotNull UUID carId,
            @NotNull @Size(min = 17, max = 17) @Pattern(regexp = "[A-HJ-NPR-Z0-9]{17}",
                    message = "VIN muss 17 gueltige Zeichen haben") String vin,
            @NotNull Boolean consentAccepted,
            Boolean autoSync,
            @Email @Size(max = 255) String xpengEmail) {}

    public record ConnectionDto(
            UUID id,
            UUID carId,
            String vin,
            String vinMasked,
            LocalDateTime consentGrantedAt,
            String consentVersion,
            LocalDateTime lastSuccessfulImportAt,
            int totalImportsCount,
            boolean autoSyncEnabled,
            LocalDateTime lastRequestSentAt,
            String xpengEmailMasked) {}

    /** Maskiert E-Mail-Adresse: "igor@example.com" -> "ig***@example.com" */
    static String maskEmail(String email) {
        if (email == null) return null;
        int at = email.indexOf('@');
        if (at <= 2) return email; // zu kurz zum sinnvoll maskieren
        return email.substring(0, 2) + "***" + email.substring(at);
    }

    private static String normalizeVin(String vin) {
        return vin == null ? null : vin.trim().toUpperCase();
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
