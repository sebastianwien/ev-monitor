package com.evmonitor.infrastructure.web;

import com.evmonitor.application.waitlist.WaitlistService;
import com.evmonitor.application.waitlist.WaitlistService.WaitlistStatus;
import com.evmonitor.domain.WaitlistFeature;
import com.evmonitor.infrastructure.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Opt-in auf Feature-Wartelisten ("benachrichtige mich, sobald verfuegbar").
 * Alle Endpoints sind ueber die Security-Config authentifiziert (JWT); der User
 * kann ausschliesslich eigene Eintraege lesen/setzen/loeschen.
 */
@RestController
@RequestMapping("/api/waitlist")
@RequiredArgsConstructor
public class WaitlistController {

    private final WaitlistService waitlistService;

    @GetMapping("/{feature}")
    public ResponseEntity<?> status(@AuthenticationPrincipal UserPrincipal principal,
                                    @PathVariable String feature) {
        UUID userId = principal.getUser().getId();
        return withFeature(feature, f -> ResponseEntity.ok(toDto(waitlistService.status(userId, f))));
    }

    @PostMapping("/{feature}")
    public ResponseEntity<?> join(@AuthenticationPrincipal UserPrincipal principal,
                                  @PathVariable String feature) {
        UUID userId = principal.getUser().getId();
        return withFeature(feature, f -> ResponseEntity.ok(toDto(waitlistService.join(userId, f))));
    }

    @DeleteMapping("/{feature}")
    public ResponseEntity<?> leave(@AuthenticationPrincipal UserPrincipal principal,
                                   @PathVariable String feature) {
        UUID userId = principal.getUser().getId();
        return withFeature(feature, f -> {
            waitlistService.leave(userId, f);
            return ResponseEntity.noContent().build();
        });
    }

    /** Parst den Feature-Pfad gegen die Allowlist und antwortet bei Unbekanntem mit 400. */
    private ResponseEntity<?> withFeature(String raw, Function<WaitlistFeature, ResponseEntity<?>> handler) {
        WaitlistFeature parsed;
        try {
            parsed = WaitlistFeature.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unbekanntes Feature"));
        }
        return handler.apply(parsed);
    }

    private WaitlistStatusDto toDto(WaitlistStatus s) {
        return new WaitlistStatusDto(s.onWaitlist(), s.since());
    }

    public record WaitlistStatusDto(boolean onWaitlist, LocalDateTime since) {}
}
