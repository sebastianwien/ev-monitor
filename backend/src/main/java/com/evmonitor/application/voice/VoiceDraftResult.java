package com.evmonitor.application.voice;

import java.util.UUID;

/**
 * Ergebnis einer Sprachaufnahme fuer die Pruefansicht. {@code fields.placeIndex} und
 * {@code fields.tariffIndex} gelten nur in diesem Request; stabil sind {@code place} und
 * {@code chargingProviderId}.
 */
public record VoiceDraftResult(String transcript, DraftFields fields, PlaceDraft place,
                               UUID chargingProviderId, VoiceQuota quota) {}
