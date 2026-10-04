package com.evmonitor.infrastructure.web;

import com.evmonitor.application.ChargingSiteService;
import com.evmonitor.domain.ChargingSiteUsage;
import com.evmonitor.infrastructure.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
