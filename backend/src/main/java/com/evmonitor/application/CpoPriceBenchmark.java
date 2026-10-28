package com.evmonitor.application;

import com.evmonitor.domain.ChargingType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Was andere Fahrer bei einem Betreiber je kWh zahlen, und welche Karten dort nachweislich
 * guenstiger sind. Nur Aggregate: nie der Preis oder die Identitaet eines einzelnen Fahrers.
 *
 * @param drivers   wie viele andere Fahrer eingehen, jeder mit dem Median seiner Ladungen
 * @param cards     guenstigste zuerst, hoechstens fuenf
 */
public record CpoPriceBenchmark(String cpoName, ChargingType chargingType, int drivers,
                                BigDecimal medianPricePerKwh,
                                BigDecimal lowerQuartilePricePerKwh,
                                BigDecimal upperQuartilePricePerKwh,
                                List<CardOffer> cards) {

    /**
     * Eine Karte mit ihrem Preis bei diesem Betreiber.
     *
     * <p>{@code DRIVERS}: Median dessen, was {@code drivers} andere Fahrer dort mit der Karte
     * gezahlt haben. Die Grundgebuehr bleibt leer - Fahrer tragen sie selten ein.
     * <p>{@code CURATED}: gepflegter Tarif, {@code verifiedOn} ist der Stand.
     */
    public record CardOffer(String name, String variant, BigDecimal pricePerKwh, BigDecimal monthlyFeeEur,
                            Source source, Integer drivers, LocalDate verifiedOn) {

        public enum Source { DRIVERS, CURATED }
    }
}
