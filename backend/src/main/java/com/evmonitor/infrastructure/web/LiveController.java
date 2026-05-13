package com.evmonitor.infrastructure.web;

import com.evmonitor.application.LiveChargingHistoryResponse;
import com.evmonitor.application.LiveChargingResponse;
import com.evmonitor.application.LiveChargingService;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.User;
import com.evmonitor.domain.exception.ForbiddenException;
import com.evmonitor.domain.exception.NotFoundException;
import com.evmonitor.infrastructure.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for live vehicle data (charging state, etc.).
 *
 * <p>Security policy:
 * <ol>
 *   <li>Caller must be authenticated (covered by SecurityConfig for all /api/**).</li>
 *   <li>Caller must own the car ({@code car.userId == user.id}).</li>
 *   <li>Caller must have Tier-2 entitlement ({@link User#canViewLiveCharging()}) -
 *       i.e. AUTOSYNC_LIVE subscription, ADMIN, or BETA_TESTER role.</li>
 * </ol>
 *
 * <p>Ownership is checked BEFORE the tier gate to avoid leaking car existence
 * to users who have the right tier but do not own the car.
 */
@RestController
@RequestMapping("/api/cars/{carId}/live")
@RequiredArgsConstructor
public class LiveController {

    private final CarRepository carRepository;
    private final LiveChargingService liveChargingService;

    /**
     * Returns the current live-charging state for a car.
     *
     * <p>Returns 200 with {@code isActive=false} when there is no active session or
     * when the connectors-service is temporarily unavailable - never a 5xx.
     */
    @GetMapping("/charging")
    public ResponseEntity<LiveChargingResponse> getLiveCharging(
            @PathVariable UUID carId,
            Authentication authentication) {

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        User user = principal.getUser();

        Car car = carRepository.findById(carId)
                .orElseThrow(() -> NotFoundException.forEntity("Car", carId));

        if (!car.getUserId().equals(user.getId())) {
            throw ForbiddenException.notOwner("Car", carId);
        }

        if (!user.canViewLiveCharging()) {
            throw new ForbiddenException("AutoSync Live entitlement required for live charging data");
        }

        LiveChargingResponse response = liveChargingService.getLiveCharging(carId);
        return ResponseEntity.ok(response);
    }

    /**
     * Returns a chronological power-over-time series for the live-charging sparkline
     * between {@code from} and {@code to} (ISO-8601). Window must be <=24h, enforced
     * on the connectors side.
     *
     * <p>Same auth chain as {@link #getLiveCharging}.
     *
     * <p>Returns 200 with an empty {@code points} list when the connectors-service is
     * unreachable - the UI just renders an empty chart.
     */
    @GetMapping("/charging/history")
    public ResponseEntity<LiveChargingHistoryResponse> getLiveChargingHistory(
            @PathVariable UUID carId,
            @RequestParam("from") String fromIso,
            @RequestParam("to") String toIso,
            Authentication authentication) {

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        User user = principal.getUser();

        Car car = carRepository.findById(carId)
                .orElseThrow(() -> NotFoundException.forEntity("Car", carId));

        if (!car.getUserId().equals(user.getId())) {
            throw ForbiddenException.notOwner("Car", carId);
        }

        if (!user.canViewLiveCharging()) {
            throw new ForbiddenException("AutoSync Live entitlement required for live charging data");
        }

        LiveChargingHistoryResponse response = liveChargingService.getLiveChargingHistory(carId, fromIso, toIso);
        return ResponseEntity.ok(response);
    }
}
