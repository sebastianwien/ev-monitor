package com.evmonitor.domain;

import java.time.LocalDateTime;

/** Ein Ladestandort aus Sicht eines Nutzers: wann zuletzt und wie oft er dort geladen hat. */
public record ChargingSiteUsage(ChargingSite site, LocalDateTime lastUsedAt, long usageCount) {}
