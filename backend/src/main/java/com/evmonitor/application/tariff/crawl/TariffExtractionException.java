package com.evmonitor.application.tariff.crawl;

/** Modell nicht erreichbar oder Antwort unbrauchbar. Text ohne Seiteninhalt, darf ins Log. */
public class TariffExtractionException extends RuntimeException {
    public TariffExtractionException(String message) {
        super(message);
    }
}
