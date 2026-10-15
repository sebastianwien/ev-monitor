package com.evmonitor.infrastructure.web;

import com.evmonitor.application.vehiclespec.MissingVehicleRequestCommand;
import com.evmonitor.application.vehiclespec.MissingVehicleRequestService;
import com.evmonitor.infrastructure.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Meldeweg "Fehlt etwas?" aus dem Auto-Anlegen. Nur eingeloggt, Rate-Limit je Nutzer und IP im Service. */
@RestController
@RequestMapping("/api/vehicle-requests")
@RequiredArgsConstructor
public class MissingVehicleRequestController {

    private final MissingVehicleRequestService service;

    @PostMapping
    public ResponseEntity<Void> submit(@Valid @RequestBody MissingVehicleRequestCommand cmd,
                                       @AuthenticationPrincipal UserPrincipal principal,
                                       HttpServletRequest request) {
        service.submit(principal.getUser().getId(), clientIp(request), cmd);
        return ResponseEntity.ok().build();
    }

    /** Echte Client-IP hinter nginx (X-Forwarded-For), sonst die Socket-Adresse. */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
