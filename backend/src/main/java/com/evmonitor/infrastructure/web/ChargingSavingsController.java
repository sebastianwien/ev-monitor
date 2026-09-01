package com.evmonitor.infrastructure.web;

import com.evmonitor.application.savings.ChargingSavings;
import com.evmonitor.application.savings.ChargingSavingsResponse;
import com.evmonitor.application.savings.HomeChargingSavingsService;
import com.evmonitor.infrastructure.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Heimlade-Ersparnis fuer den angemeldeten Nutzer.
 *
 * Der Nutzer kommt ausschliesslich aus dem Token - es gibt keinen Weg, die Zahlen eines
 * fremden Kontos abzufragen.
 */
@RestController
@RequestMapping("/api/stats/charging-savings")
@RequiredArgsConstructor
public class ChargingSavingsController {

    private final HomeChargingSavingsService service;

    /**
     * @return 401 ohne Token, 403 ohne bezahlten Tarif, 204 wenn Heim- oder Vergleichspreis
     *         unbekannt sind - die Kachel zeigt dann ihren Leerzustand, geschaetzt wird nicht.
     */
    @GetMapping
    public ResponseEntity<ChargingSavingsResponse> get(@AuthenticationPrincipal UserPrincipal principal) {
        // Die Security-Config endet auf anyRequest().permitAll(), der fehlende Token muss
        // deshalb hier abgefangen werden.
        if (principal == null || principal.getUser() == null) {
            return ResponseEntity.status(401).build();
        }
        // Gate auf die bezahlten Tarife (AutoSync-Leiter und Supporter). Ob die Kachel
        // frei wird, ist eine offene Produktentscheidung - sie haengt an dieser Zeile
        // beziehungsweise an User#canViewChargingSavings().
        if (!principal.getUser().canViewChargingSavings()) {
            return ResponseEntity.status(403).build();
        }
        UUID userId = principal.getUser().getId();
        ChargingSavings savings = service.calculate(userId);
        return savings == null
                ? ResponseEntity.noContent().build()
                : ResponseEntity.ok(ChargingSavingsResponse.from(savings));
    }
}
