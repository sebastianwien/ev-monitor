package com.evmonitor.application;

import java.time.LocalDateTime;
import java.util.UUID;

public record EvLogSavedEvent(UUID logId, String geohash, LocalDateTime loggedAt, Double temperatureCelsius) {}
