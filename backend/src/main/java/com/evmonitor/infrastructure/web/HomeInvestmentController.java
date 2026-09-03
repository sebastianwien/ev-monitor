package com.evmonitor.infrastructure.web;

import com.evmonitor.application.savings.HomeInvestmentService;
import com.evmonitor.infrastructure.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * Wallbox samt Installation. Haengt am Nutzer, nicht am Auto - eine Wallbox gehoert
 * zum Haushalt und wird nicht pro Fahrzeug angeschafft.
 */
@RestController
@RequestMapping("/api/users/me/home-investment")
@RequiredArgsConstructor
public class HomeInvestmentController {

    /** Oberhalb davon ist es kein Tippfehler mehr, sondern Unsinn - und macht die
     *  Amortisationszeile unbrauchbar. */
    private static final BigDecimal MAX_INVESTMENT = new BigDecimal("100000");

    private final HomeInvestmentService service;

    @PatchMapping
    public ResponseEntity<Void> update(@AuthenticationPrincipal UserPrincipal principal,
                                       @RequestBody HomeInvestmentRequest request) {
        // Die Security-Config endet auf anyRequest().permitAll().
        if (principal == null || principal.getUser() == null) {
            return ResponseEntity.status(401).build();
        }
        BigDecimal amount = request != null ? request.investmentEur() : null;
        if (amount != null && (amount.signum() < 0 || amount.compareTo(MAX_INVESTMENT) > 0)) {
            return ResponseEntity.badRequest().build();
        }
        service.update(principal.getUser().getId(), amount);
        return ResponseEntity.noContent().build();
    }

    /** @param investmentEur null loescht den Wert */
    public record HomeInvestmentRequest(BigDecimal investmentEur) {}
}
