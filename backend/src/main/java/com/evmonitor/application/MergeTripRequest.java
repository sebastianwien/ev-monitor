package com.evmonitor.application;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record MergeTripRequest(@NotNull UUID mergeWithTripId) {}
