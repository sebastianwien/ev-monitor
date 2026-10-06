package com.evmonitor.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Eine öffentliche Ladung von heute für den "Heute"-Eintrag im Ticker.
 * {@code userId} dient nur dazu, höchstens eine Ladung je Nutzer zu zeigen, und verlässt das Backend nie.
 * {@code provider} ist null, wenn weniger als drei Nutzer diesen Anbieternamen verwenden.
 */
public record TodayChargeRow(UUID userId, BigDecimal kwh, BigDecimal costEur, String provider) {
}
