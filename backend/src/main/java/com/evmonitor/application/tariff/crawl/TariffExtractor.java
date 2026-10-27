package com.evmonitor.application.tariff.crawl;

import java.util.List;

/**
 * Liest Tarife aus dem Text einer Seite (Sprachmodell). Liefert nur, was auf der Seite steht;
 * jedes Angebot trägt ein wörtliches Zitat, das {@link EvidenceCheck} gegen den Text prüft.
 */
public interface TariffExtractor {

    /** false ohne Konfiguration (kein API-Key); der Crawler liest dann nur WATCH-Quellen. */
    boolean isAvailable();

    /** Wirft {@link TariffExtractionException}, wenn das Modell nicht antwortet oder Unlesbares liefert. */
    Extraction extract(Request request);

    /**
     * @param country  Land, für das Preise gelten sollen ("DE", "AT")
     * @param known    gepflegte Tarife dieses Anbieters, damit das Modell deren Namen wiederverwendet
     * @param catalog  Betreibernamen aus dem Katalog, in deren Schreibweise das Modell antworten soll
     */
    record Request(String empName, String country, String url, String pageText, List<String> priceImages,
                   List<ExtractedTariff> known, List<String> catalog) {}

    record Extraction(List<ExtractedTariff> tariffs, List<TierMembers> tiers, Usage usage) {}

    /** Betreiber, die eine Seite einer Preisstufe zuordnet (Maingau: Niedrig-, Standard-, Hochpreis). */
    record TierMembers(String tier, List<OperatorName> operators) {}

    /** {@code onPage} wie auf der Seite geschrieben, {@code catalog} der Katalogname oder null. */
    record OperatorName(String onPage, String catalog) {}

    record Usage(String model, Integer promptTokens, Integer completionTokens) {}
}
