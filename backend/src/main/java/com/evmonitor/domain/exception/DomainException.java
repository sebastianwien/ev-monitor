package com.evmonitor.domain.exception;

import lombok.Getter;

/**
 * Basis aller fachlichen Fehler. Wird vom GlobalExceptionHandler auf
 * passende HTTP-Statuscodes gemappt. Die Domain selbst kennt kein HTTP.
 *
 * <p>Der {@code code} ist ein maschinenlesbarer Error-Key, der im Response-Body
 * landet und vom Frontend für i18n / UI-Logik genutzt werden kann. Die
 * {@code message} ist die (optional lokalisierte) Default-Repräsentation.
 */
@Getter
public sealed abstract class DomainException extends RuntimeException
        permits NotFoundException, ForbiddenException, ConflictException, ValidationException, AuthException {

    private final String code;

    protected DomainException(String code, String message) {
        super(message);
        this.code = code;
    }
}
