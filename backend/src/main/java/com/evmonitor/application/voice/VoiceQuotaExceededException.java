package com.evmonitor.application.voice;

public class VoiceQuotaExceededException extends RuntimeException {

    private final VoiceQuota quota;

    public VoiceQuotaExceededException(VoiceQuota quota) {
        super("Monatsdeckel fuer Sprachaufnahmen erreicht");
        this.quota = quota;
    }

    public VoiceQuota quota() {
        return quota;
    }
}
