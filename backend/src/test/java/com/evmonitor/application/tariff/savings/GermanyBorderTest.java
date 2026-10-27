package com.evmonitor.application.tariff.savings;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Is a charge in Germany? Its geohash centre against the Natural Earth 1:50m border (about one
 * to two kilometres exact). Without geohash nobody knows, and the answer is no.
 */
class GermanyBorderTest {

    @Test
    void citiesInGermany_includingIslandsAndBorderTowns() {
        assertThat(GermanyBorder.contains(52.52, 13.405)).as("Berlin").isTrue();
        assertThat(GermanyBorder.contains(48.137, 11.575)).as("München").isTrue();
        assertThat(GermanyBorder.contains(50.776, 6.084)).as("Aachen").isTrue();
        assertThat(GermanyBorder.contains(54.91, 8.31)).as("Sylt").isTrue();
        assertThat(GermanyBorder.contains(47.62, 7.67)).as("Lörrach").isTrue();
    }

    @Test
    void neighbouringCities_areOutside() {
        assertThat(GermanyBorder.contains(47.80, 13.045)).as("Salzburg").isFalse();
        assertThat(GermanyBorder.contains(47.071, 15.439)).as("Graz").isFalse();
        assertThat(GermanyBorder.contains(47.559, 7.588)).as("Basel").isFalse();
        assertThat(GermanyBorder.contains(48.573, 7.752)).as("Strasbourg").isFalse();
        assertThat(GermanyBorder.contains(52.37, 4.895)).as("Amsterdam").isFalse();
        assertThat(GermanyBorder.contains(50.075, 14.437)).as("Prag").isFalse();
    }

    @Test
    void geohash_isDecodedToItsCentre() {
        assertThat(GermanyBorder.containsGeohash("u33dc0")).as("Berlin Mitte").isTrue();
        assertThat(GermanyBorder.containsGeohash("u23rbv")).as("Salzburg").isFalse();
        assertThat(GermanyBorder.containsGeohash(null)).isFalse();
        assertThat(GermanyBorder.containsGeohash("ä!")).isFalse();
    }
}
