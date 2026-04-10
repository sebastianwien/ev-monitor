package com.evmonitor.domain.exception;

/**
 * Geworfen bei Eindeutigkeits- oder Zustands-Konflikten (z.B. E-Mail schon
 * vergeben, Duplicate-Key). Mapped auf HTTP 409.
 */
public final class ConflictException extends DomainException {

    public ConflictException(String code, String message) {
        super(code, message);
    }

    public static ConflictException emailTaken() {
        return new ConflictException("EMAIL_TAKEN", "Email is already in use.");
    }

    public static ConflictException usernameTaken() {
        return new ConflictException("USERNAME_TAKEN", "Username is already taken.");
    }
}
