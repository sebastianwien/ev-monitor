package com.evmonitor.application.voice;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Mistral-Nutzung des Sprachlogs fuer das Admin-Dashboard, aggregiert aus {@code voice_draft}.
 * Ausschliesslich Nutzungsmetadaten, keine Inhalte.
 *
 * @param periodDays     tatsaechlich ausgewerteter Zeitraum in Tagen (nach Clamping)
 * @param allTimeCostUsd Summe aller je eingefrorenen Kosten, unabhaengig vom Zeitraum
 */
public record VoiceUsageInsights(int periodDays, Totals totals, List<Day> days, List<Model> models,
                                 List<Error> errors, List<TopUser> topUsers, BigDecimal allTimeCostUsd) {

    /** Summen des Zeitraums; Kosten je Erfolg = alle Kosten (auch Fehlschlaege) durch Erfolge. */
    public record Totals(long calls, long successCalls, long failedCalls, long distinctUsers, BigDecimal audioSeconds,
                         long transcribeTokens, long extractPromptTokens, long extractCompletionTokens, BigDecimal costUsd,
                         long avgLatencyMs, double avgFieldsFilled, double avgUncertain, BigDecimal costPerSuccessUsd) {}

    public record Day(String date, long calls, long failedCalls, BigDecimal audioSeconds, long transcribeTokens,
                      long extractPromptTokens, long extractCompletionTokens, BigDecimal costUsd, long avgLatencyMs) {}

    /** @param kind "transcribe" oder "extract"; Audio-Sekunden nur bei transcribe, Tokens je nach Stufe */
    public record Model(String name, String kind, long calls, long tokens, BigDecimal audioSeconds) {}

    public record Error(String code, long count) {}

    public record TopUser(UUID userId, long calls, long failedCalls, BigDecimal costUsd) {}
}
