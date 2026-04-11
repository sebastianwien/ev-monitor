package com.evmonitor.domain.exception;

/**
 * Geworfen wenn eine angeforderte Ressource nicht existiert.
 * Mapped auf HTTP 404.
 */
public final class NotFoundException extends DomainException {

    public NotFoundException(String message) {
        super("NOT_FOUND", message);
    }

    public static NotFoundException forEntity(String entity, Object id) {
        return new NotFoundException(entity + " not found: " + id);
    }
}
