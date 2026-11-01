package com.evmonitor.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * Monat eines Ticker-Aufrufs. Ohne Angabe der laufende Monat, sonst "YYYY-MM"; erlaubt sind nur
 * der laufende Monat und der Vormonat (mehr braucht der Monatsrückblick nicht, und jeder Monat ist
 * ein eigener Cache-Eintrag). Das Fenster reicht vom Monatsersten bis zum Monatsende, im laufenden
 * Monat bis heute einschließlich, so wie der Ticker immer gerechnet hat.
 */
public record TickerMonth(YearMonth month, LocalDate firstDay, LocalDate lastDay) {

    public static TickerMonth of(YearMonth month, LocalDate today) {
        LocalDate endOfMonth = month.atEndOfMonth();
        return new TickerMonth(month, month.atDay(1), endOfMonth.isBefore(today) ? endOfMonth : today);
    }

    /** Leer heißt laufender Monat; alles außer laufendem Monat und Vormonat ist ungültig. */
    public static Optional<YearMonth> parse(String raw, LocalDate today) {
        YearMonth current = YearMonth.from(today);
        if (raw == null || raw.isBlank()) return Optional.of(current);
        try {
            YearMonth requested = YearMonth.parse(raw.trim());
            boolean allowed = requested.equals(current) || requested.equals(current.minusMonths(1));
            return allowed ? Optional.of(requested) : Optional.empty();
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    public LocalDateTime start() {
        return firstDay.atStartOfDay();
    }

    /** Exklusiv: 0 Uhr des Tages nach dem letzten Tag. */
    public LocalDateTime end() {
        return lastDay.plusDays(1).atStartOfDay();
    }

    /** Monat als Zahl 1-12; den Namen übersetzt das Frontend. */
    public String monthParam() {
        return String.valueOf(month.getMonthValue());
    }
}
