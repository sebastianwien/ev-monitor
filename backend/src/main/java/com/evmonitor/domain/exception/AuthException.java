package com.evmonitor.domain.exception;

/**
 * Geworfen bei Auth-/Token-Flows (Email-Verifizierung, Password-Reset,
 * Rate-Limiting). Mapped je nach {@code code} auf passenden Status:
 * <ul>
 *   <li>{@code EMAIL_NOT_VERIFIED} → 403 Forbidden</li>
 *   <li>{@code TOKEN_EXPIRED} → 410 Gone</li>
 *   <li>{@code INVALID_TOKEN} → 400 Bad Request</li>
 *   <li>{@code RATE_LIMITED} → 429 Too Many Requests</li>
 * </ul>
 * Die {@code getMessage()} gibt denselben String zurück wie {@code getCode()},
 * da Tests historisch gegen die Message prüfen.
 */
public final class AuthException extends DomainException {

    private AuthException(String code) {
        super(code, code);
    }

    public static AuthException emailNotVerified() {
        return new AuthException("EMAIL_NOT_VERIFIED");
    }

    public static AuthException tokenExpired() {
        return new AuthException("TOKEN_EXPIRED");
    }

    public static AuthException invalidToken() {
        return new AuthException("INVALID_TOKEN");
    }

    public static AuthException rateLimited() {
        return new AuthException("RATE_LIMITED");
    }
}
