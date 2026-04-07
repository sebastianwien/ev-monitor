package com.evmonitor.domain;

import org.springframework.lang.Nullable;

public record CapacityEntry(double kWh, @Nullable String variantName) {}
