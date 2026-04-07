package com.evmonitor.infrastructure.web;

import com.evmonitor.application.user.*;
import com.evmonitor.infrastructure.security.CustomUserDetailsService;
import com.evmonitor.infrastructure.security.JwtService;
import com.evmonitor.infrastructure.security.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final JwtService jwtService;
    private final CustomUserDetailsService customUserDetailsService;

    @GetMapping("/me/stats")
    public ResponseEntity<UserStatsResponse> getUserStats(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID userId = UUID.fromString(principal.getUser().getId().toString());
        UserStatsResponse stats = userService.getUserStats(userId);
        return ResponseEntity.ok(stats);
    }

    @PutMapping("/me/email")
    public ResponseEntity<Void> changeEmail(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ChangeEmailRequest request
    ) {
        UUID userId = UUID.fromString(principal.getUser().getId().toString());
        userService.changeEmail(userId, request);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/me/username")
    public ResponseEntity<Map<String, String>> changeUsername(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ChangeUsernameRequest request
    ) {
        UUID userId = UUID.fromString(principal.getUser().getId().toString());
        userService.changeUsername(userId, request);
        UserDetails updatedUser = customUserDetailsService.loadUserById(userId);
        String newToken = jwtService.generateToken(updatedUser);
        return ResponseEntity.ok(Map.of("token", newToken));
    }

    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        UUID userId = UUID.fromString(principal.getUser().getId().toString());
        userService.changePassword(userId, request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me/export")
    public ResponseEntity<Resource> exportUserData(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID userId = UUID.fromString(principal.getUser().getId().toString());
        byte[] data = userService.exportUserData(userId);

        ByteArrayResource resource = new ByteArrayResource(data);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"ev-monitor-export-" + System.currentTimeMillis() + ".json\"")
                .body(resource);
    }

    @PutMapping("/me/country")
    public ResponseEntity<Map<String, String>> updateCountry(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam @Size(min = 2, max = 2) String country
    ) {
        UUID userId = UUID.fromString(principal.getUser().getId().toString());
        userService.updateCountry(userId, country.toUpperCase());
        UserDetails updatedUser = customUserDetailsService.loadUserById(userId);
        String newToken = jwtService.generateToken(updatedUser);
        return ResponseEntity.ok(Map.of("token", newToken));
    }

    @PutMapping("/me/leaderboard-visible")
    public ResponseEntity<Void> setLeaderboardVisible(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam boolean visible
    ) {
        UUID userId = UUID.fromString(principal.getUser().getId().toString());
        userService.setLeaderboardVisible(userId, visible);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteAccount(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody DeleteAccountRequest request
    ) {
        UUID userId = UUID.fromString(principal.getUser().getId().toString());
        userService.deleteAccount(userId, request);
        return ResponseEntity.ok().build();
    }
}
