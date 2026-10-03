package com.evmonitor.application.voice;

/** Fehlschlag beim Sprachanbieter. {@link Reason} landet als {@code error_code} in {@code voice_draft}. */
public class VoiceProviderException extends RuntimeException {

    public enum Reason { RATE_LIMITED, REJECTED, UNAVAILABLE, INVALID_RESPONSE, NOT_CONFIGURED, EMPTY_TRANSCRIPT }

    private final Reason reason;

    public VoiceProviderException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
