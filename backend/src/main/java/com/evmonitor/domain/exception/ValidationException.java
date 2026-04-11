package com.evmonitor.domain.exception;

/**
 * Geworfen bei ungültigem Input oder Zustand der nicht eindeutig einem
 * anderen Typ zuzuordnen ist. Mapped auf HTTP 400.
 */
public final class ValidationException extends DomainException {

    public ValidationException(String message) {
        super("VALIDATION_ERROR", message);
    }

    public ValidationException(String code, String message) {
        super(code, message);
    }
}
