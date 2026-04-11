package com.evmonitor.domain.exception;

import java.util.UUID;

/**
 * Geworfen bei Ownership- oder Autorisierungs-Verletzungen (User darf
 * auf diese Resource nicht zugreifen). Mapped auf HTTP 403.
 */
public final class ForbiddenException extends DomainException {

    public ForbiddenException(String message) {
        super("FORBIDDEN", message);
    }

    public static ForbiddenException notOwner(String entity, UUID id) {
        return new ForbiddenException("User does not own " + entity + " " + id);
    }
}
