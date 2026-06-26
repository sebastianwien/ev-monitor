package com.evmonitor.infrastructure.web;

import com.evmonitor.application.LeaderboardResponseDTO;
import com.evmonitor.application.LeaderboardService;
import com.evmonitor.application.MyLeaderboardStandingDTO;
import com.evmonitor.application.TickerItemDTO;
import com.evmonitor.domain.LeaderboardCategory;
import com.evmonitor.infrastructure.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Public leaderboard endpoints. No auth required for top 10 data.
 * JWT is optional: when provided, the user's own rank is included in the response
 * if they are not already in the top 10.
 */
@RestController
@RequestMapping("/api/public/leaderboard")
@RequiredArgsConstructor
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    /**
     * GET /api/public/leaderboard/{category}?monthsBack=0
     * Returns the top 10 for the given category. monthsBack=0 (default) is the current month;
     * monthsBack=1 returns last month's final standings (reference date = last day of previous month).
     * If a JWT is present and the user is not in the top 10, their own rank is also returned.
     */
    @GetMapping("/{category}")
    public ResponseEntity<LeaderboardResponseDTO> getLeaderboard(
            @PathVariable LeaderboardCategory category,
            @RequestParam(defaultValue = "0") int monthsBack,
            @AuthenticationPrincipal UserPrincipal principal) {

        if (monthsBack < 0 || monthsBack > 1) {
            return ResponseEntity.badRequest().build();
        }
        LocalDate referenceDate = monthsBack == 0
                ? LocalDate.now()
                : LocalDate.now().withDayOfMonth(1).minusDays(1); // letzter Tag des Vormonats

        UUID requestingUserId = principal != null ? principal.getUser().getId() : null;
        return ResponseEntity.ok(leaderboardService.getLeaderboard(category, requestingUserId, referenceDate));
    }

    /**
     * GET /api/public/leaderboard/me
     * Returns the authenticated user's rank and value across all categories for the current month.
     * Requires JWT.
     */
    @GetMapping("/standings/me")
    public ResponseEntity<List<MyLeaderboardStandingDTO>> getMyStandings(
            @AuthenticationPrincipal UserPrincipal principal) {

        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(leaderboardService.getMyStandings(principal.getUser().getId()));
    }

    /**
     * GET /api/public/leaderboard/ticker
     * Returns ticker items: category leaders, community stats, fun facts.
     */
    @GetMapping("/ticker")
    public ResponseEntity<List<TickerItemDTO>> getTicker() {
        return ResponseEntity.ok(leaderboardService.getTicker());
    }
}
