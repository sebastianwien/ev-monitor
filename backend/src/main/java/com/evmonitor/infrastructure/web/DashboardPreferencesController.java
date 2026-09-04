package com.evmonitor.infrastructure.web;

import com.evmonitor.application.dashboard.DashboardPreferencesService;
import com.evmonitor.infrastructure.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sichtbarkeit der Dashboard-Kacheln des angemeldeten Nutzers.
 *
 * Der Nutzer kommt ausschliesslich aus dem Token - es gibt keinen Weg, die Kacheln eines
 * fremden Kontos zu schalten.
 */
@RestController
@RequestMapping("/api/users/me/dashboard-preferences")
@RequiredArgsConstructor
public class DashboardPreferencesController {

    private final DashboardPreferencesService service;

    @GetMapping
    public ResponseEntity<DashboardPreferencesResponse> get(@AuthenticationPrincipal UserPrincipal principal) {
        // Die Security-Config endet auf anyRequest().permitAll().
        if (principal == null || principal.getUser() == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(new DashboardPreferencesResponse(
                service.isSavingsCardDismissed(principal.getUser().getId())));
    }

    @PatchMapping
    public ResponseEntity<Void> update(@AuthenticationPrincipal UserPrincipal principal,
                                       @RequestBody DashboardPreferencesRequest request) {
        if (principal == null || principal.getUser() == null) {
            return ResponseEntity.status(401).build();
        }
        // Ohne Flag ist die Absicht unklar - lieber ablehnen als raten.
        if (request == null || request.savingsCardDismissed() == null) {
            return ResponseEntity.badRequest().build();
        }
        service.setSavingsCardDismissed(principal.getUser().getId(), request.savingsCardDismissed());
        return ResponseEntity.noContent().build();
    }

    public record DashboardPreferencesRequest(Boolean savingsCardDismissed) {}

    public record DashboardPreferencesResponse(boolean savingsCardDismissed) {}
}
