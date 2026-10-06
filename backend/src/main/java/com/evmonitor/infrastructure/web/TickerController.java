package com.evmonitor.infrastructure.web;

import com.evmonitor.application.PersonalTickerService;
import com.evmonitor.application.TickerItemDTO;
import com.evmonitor.application.TickerTodayService;
import com.evmonitor.infrastructure.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Ticker-Einträge nur für eingeloggte Nutzer. Liegt bewusst nicht unter /api/public: die
 * "Heute"-Ladungen sollen nur im Ticker eingeloggter Nutzer erscheinen, nicht auf der Landing Page.
 */
@RestController
@RequestMapping("/api/ticker")
@RequiredArgsConstructor
public class TickerController {

    private final PersonalTickerService personalTickerService;
    private final TickerTodayService tickerTodayService;

    @GetMapping("/me")
    public ResponseEntity<List<TickerItemDTO>> getMyTicker(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(personalTickerService.getItems(principal.getUser().getId()));
    }

    /** Bis zu zwei anonyme öffentliche Ladungen von heute (für alle gleich, 15 min gecacht). */
    @GetMapping("/today")
    public ResponseEntity<List<TickerItemDTO>> getToday() {
        return ResponseEntity.ok(tickerTodayService.getTodayItems());
    }
}
