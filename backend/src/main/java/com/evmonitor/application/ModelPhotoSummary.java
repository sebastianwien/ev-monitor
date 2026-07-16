package com.evmonitor.application;

import java.util.UUID;

/**
 * Per-model summary of publicly shared user photos, for the model list page.
 * {@code model} is the {@code CarModel} enum name (matches {@link TopModelResponse#model()}),
 * {@code heroCarId} is the newest public photo, {@code count} the total available.
 * Carries no user-identifiable data.
 */
public record ModelPhotoSummary(String model, UUID heroCarId, int count) {}
