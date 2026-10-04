package com.evmonitor.infrastructure.web;

import com.evmonitor.application.ChargingSiteService;
import com.evmonitor.application.KnownPlace;
import com.evmonitor.application.Position;
import com.evmonitor.domain.ChargingSiteUsage;
import com.evmonitor.infrastructure.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/charging-sites")
@RequiredArgsConstructor
public class ChargingSiteController {

    private final ChargingSiteService chargingSiteService;

    /** Zuletzt genutzte Ladestandorte des angemeldeten Nutzers, fuer den Ortsschritt im Wizard. */
    @GetMapping("/recent")
    public List<RecentChargingSiteResponse> recent(Authentication authentication) {
        UUID userId = ((UserPrincipal) authentication.getPrincipal()).getUser().getId();
        return chargingSiteService.recentlyUsed(userId).stream().map(RecentChargingSiteResponse::from).toList();
    }

    /**
     * Die Orte, an denen der Nutzer schon geladen hat, fuer den Ortsschritt: ohne Position seine
     * haeufigsten, mit Position nur die in der Zelle der Position ("hier"). Die Koordinaten werden nur mit den eigenen Logs
     * verglichen, nie gespeichert; an den Geocoder geht nur die Mitte einer Zelle aus den eigenen Logs.
     */
    @GetMapping("/known")
    public ResponseEntity<List<KnownPlaceResponse>> known(@RequestParam(required = false) Double lat,
                                                          @RequestParam(required = false) Double lon,
                                                          Authentication authentication) {
        Position at = null;
        if (lat != null || lon != null) {
            if (lat == null || lon == null || !Position.onEarth(lat, lon)) return ResponseEntity.badRequest().build();
            at = new Position(lat, lon);
        }
        UUID userId = ((UserPrincipal) authentication.getPrincipal()).getUser().getId();
        return ResponseEntity.ok(chargingSiteService.knownPlaces(userId, at).stream().map(KnownPlaceResponse::from).toList());
    }

    /**
     * Der Ort "hier" allein: steht der Nutzer an einem Ort, an dem er schon geladen hat? Kein
     * Treffer ist 204 - fuer den Aufrufer dasselbe wie "Liste zeigen".
     */
    @GetMapping("/suggestion")
    public ResponseEntity<KnownPlaceResponse> suggestion(@RequestParam double lat, @RequestParam double lon,
                                                         Authentication authentication) {
        if (!Position.onEarth(lat, lon)) {
            return ResponseEntity.badRequest().build();
        }
        UUID userId = ((UserPrincipal) authentication.getPrincipal()).getUser().getId();
        return chargingSiteService.suggest(userId, lat, lon)
                .map(p -> ResponseEntity.ok(KnownPlaceResponse.from(p)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * @param placeName Ortsteil fuer Orte ohne Saeule, null wenn unbekannt (Frontend zeigt dann Anbieter oder "Privat")
     * @param lastProviderId Ladekarte der letzten Ladung dort, null ohne Karte
     */
    public record KnownPlaceResponse(String geohash, boolean isPublic, long usageCount, LocalDateTime lastUsedAt,
                                     String cpoName, UUID lastProviderId, String placeName,
                                     RecentChargingSiteResponse site, boolean here) {
        static KnownPlaceResponse from(KnownPlace p) {
            var c = p.cell();
            var site = p.site() == null ? null : RecentChargingSiteResponse.from(new ChargingSiteUsage(p.site(), c.lastUsedAt(), c.usageCount()));
            return new KnownPlaceResponse(c.geohash(), c.isPublic(), c.usageCount(), c.lastUsedAt(), c.cpoName(),
                    c.lastProviderId(), p.placeName(), site, p.here());
        }
    }

    public record RecentChargingSiteResponse(UUID id, String name, String cpoName, String geohash,
                                             BigDecimal maxAcKw, BigDecimal maxDcKw, int chargePoints, boolean fastCharging,
                                             String address, java.util.List<String> plugTypes,
                                             LocalDateTime lastUsedAt, long usageCount) {
        static RecentChargingSiteResponse from(ChargingSiteUsage u) {
            var s = u.site();
            return new RecentChargingSiteResponse(s.id(), s.name(), s.cpoName(), s.geohash(), s.maxAcKw(), s.maxDcKw(),
                    s.chargePoints(), s.fastCharging(), s.register().address(), s.register().plugTypes(),
                    u.lastUsedAt(), u.usageCount());
        }
    }
}
