package com.evmonitor.infrastructure.web;

import com.evmonitor.application.CoinBalanceResponse;
import com.evmonitor.application.CoinLogResponse;
import com.evmonitor.application.CoinLogService;
import com.evmonitor.domain.CoinType;
import com.evmonitor.infrastructure.security.UserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/coins")
@CrossOrigin(origins = "*")
public class CoinLogController {

    private final CoinLogService coinLogService;

    public CoinLogController(CoinLogService coinLogService) {
        this.coinLogService = coinLogService;
    }

    /**
     * Get current user's coin balance.
     * GET /api/coins/balance
     */
    @GetMapping("/balance")
    public ResponseEntity<CoinBalanceResponse> getCoinBalance(Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        CoinBalanceResponse balance = coinLogService.getCoinBalance(principal.getUser().getId());
        return ResponseEntity.ok(balance);
    }

    /**
     * Get all coin logs for current user.
     * GET /api/coins/logs
     */
    @GetMapping("/logs")
    public ResponseEntity<List<CoinLogResponse>> getCoinLogs(
            @RequestParam(required = false) CoinType coinType,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        List<CoinLogResponse> logs;
        if (coinType != null) {
            logs = coinLogService.getCoinLogsByType(principal.getUser().getId(), coinType);
        } else {
            logs = coinLogService.getCoinLogsForUser(principal.getUser().getId());
        }

        return ResponseEntity.ok(logs);
    }
}
