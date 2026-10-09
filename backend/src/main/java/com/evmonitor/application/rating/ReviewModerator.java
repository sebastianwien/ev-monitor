package com.evmonitor.application.rating;

import java.math.BigDecimal;

/** Prüft einen Erfahrungstext und empfiehlt eine Entscheidung. Entscheiden tut immer ein Mensch. */
public interface ReviewModerator {

    /** Was Mistral sieht: Modell, Textart, Messwerte und der Text. Keine IDs, kein Nutzerbezug. */
    record Input(String modelName, boolean report, String text, Integer kmDriven, Integer logCount,
                 BigDecimal consumptionKwhPer100km) {
    }

    /** Antwort als JSON nach dem Ausgabe-Schema plus Nutzung für die Kostenübersicht. */
    record Result(String json, String model, Integer promptTokens, Integer completionTokens, BigDecimal costUsd) {
    }

    /** @throws com.evmonitor.application.voice.VoiceProviderException wenn Mistral nicht erreichbar oder nicht konfiguriert ist */
    Result moderate(Input input);
}
