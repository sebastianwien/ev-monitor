package com.evmonitor.infrastructure.web;

import com.evmonitor.application.vehiclespec.MissingVehicleRequestResponse;
import com.evmonitor.application.vehiclespec.MissingVehicleRequestService;
import com.evmonitor.infrastructure.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Arbeitsliste "Gemeldet" im Admin-Tab Fahrzeug-Specs. "Erledigt" schickt die Nutzer-Rückmeldung. */
@RestController
@RequestMapping("/api/admin/vehicle-requests")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminMissingVehicleRequestController {

    private final MissingVehicleRequestService service;

    @GetMapping
    public List<MissingVehicleRequestResponse> open() {
        return service.openRequests();
    }

    @PostMapping("/{id}/done")
    public void markDone(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal admin) {
        service.markDone(admin.getUser().getId(), id);
    }
}
