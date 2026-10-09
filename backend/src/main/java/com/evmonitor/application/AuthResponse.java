package com.evmonitor.application;

import java.util.UUID;

public record AuthResponse(
        String token,
        UUID userId,
        String email,
        String role,
        boolean isDemoAccount,
        boolean isPremium,
        boolean canActivateTelemetry) {

    /** Response for a regular (non-demo) login of {@code user}; seed users count as demo accounts. */
    public static AuthResponse forUser(String token, com.evmonitor.domain.User user) {
        return new AuthResponse(token, user.getId(), user.getEmail(), user.getRole(),
                user.isSeedData(), user.isPremium(), user.canActivateTelemetry());
    }
}
