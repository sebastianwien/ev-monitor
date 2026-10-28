package com.evmonitor.application.tariff.crawl;

import java.math.BigDecimal;

/**
 * Ein Tarif, wie er auf einer Seite steht. {@code null} heißt "steht nicht da", nicht "0".
 *
 * @param scope     OWN_NETWORK (eigene Säulen), OPERATOR (genannter Betreiber), TIER (Preisstufe),
 *                  ELSEWHERE (alle anderen Betreiber)
 * @param operator        Betreiber im Katalog-Namen (nur OPERATOR)
 * @param operatorOnPage  derselbe Betreiber, wie er auf der Seite steht; wird gegen den Text geprüft
 * @param evidence        wörtliches Zitat der Seite mit dem Preis
 */
public record ExtractedTariff(String variant, String chargingType, BigDecimal pricePerKwh,
                              BigDecimal monthlyFeeEur, BigDecimal sessionFeeEur,
                              BigDecimal blockingFeePerMin, Integer blockingFeeAfterMin, BigDecimal blockingFeeCapEur,
                              Scope scope, String operator, String operatorOnPage, String tier, Integer minPowerKw,
                              boolean dynamic, String evidence) {

    public enum Scope { OWN_NETWORK, OPERATOR, TIER, ELSEWHERE }
}
