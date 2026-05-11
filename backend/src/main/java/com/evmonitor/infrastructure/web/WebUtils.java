package com.evmonitor.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;

/** Helpers for extracting metadata from HTTP requests (client IP, etc.). */
public final class WebUtils {
    private WebUtils() {}

    /**
     * Returns the originating client IP. Honors {@code X-Forwarded-For} when set
     * (taking the first hop), otherwise falls back to the remote address.
     * Safe to call without nginx in front - returns {@code remoteAddr} as-is.
     */
    public static String clientIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}
