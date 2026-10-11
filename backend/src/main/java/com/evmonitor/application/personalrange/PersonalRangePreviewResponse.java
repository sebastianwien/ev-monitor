package com.evmonitor.application.personalrange;

/**
 * The range band shown before the gate on the model page: winter to summer range for the
 * visitor's inputs, nothing else. dataLevel rates only the two season values behind it.
 */
public record PersonalRangePreviewResponse(int minKm, int maxKm, PersonalRangeResponse.DataLevel dataLevel) {}
