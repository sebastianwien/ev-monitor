package com.evmonitor.application.voice;

import java.math.BigDecimal;

/**
 * Verbrauch eines Mistral-Aufrufs laut {@code usage}-Block der Antwort - Grundlage fuer die
 * Kostenerfassung je Nutzer.
 *
 * @param model        konfigurierter Modellname (Preisschluessel), nicht der aufgeloeste der Antwort
 * @param audioSeconds {@code prompt_audio_seconds}, null bei reinen Textaufrufen
 * @param costUsd      beim Aufruf aus den Preis-Properties berechnet und damit eingefroren
 */
public record VoiceUsage(String model, Double audioSeconds, Integer promptTokens, Integer completionTokens,
                         BigDecimal costUsd) {}
