package com.evmonitor.infrastructure.web;

import com.evmonitor.application.LeaderboardResponseDTO;
import com.evmonitor.application.LeaderboardService;
import com.evmonitor.application.MyLeaderboardStandingDTO;
import com.evmonitor.application.TickerItemDTO;
import com.evmonitor.domain.LeaderboardCategory;
import com.evmonitor.infrastructure.security.UserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Public leaderboard endpoints. No auth required for top 10 data.
 * JWT is optional: when provided, the user's own rank is included in the response
 * if they are not already in the top 10.
 */
@RestController
@RequestMapping("/api/public/leaderboard")
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    public LeaderboardController(LeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }

    /**
     * GET /api/public/leaderboard/{category}
     * Returns the top 10 for the given category in the current month.
     * If a JWT is present and the user is not in the top 10, their own rank is also returned.
     */
    @GetMapping("/{category}")
    public ResponseEntity<LeaderboardResponseDTO> getLeaderboard(
            @PathVariable LeaderboardCategory category,
            @AuthenticationPrincipal UserPrincipal principal) {

        UUID requestingUserId = principal != null ? principal.getUser().getId() : null;
        return ResponseEntity.ok(leaderboardService.getLeaderboard(category, requestingUserId));
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
