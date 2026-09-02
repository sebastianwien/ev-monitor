package com.evmonitor.application.savings;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import com.evmonitor.infrastructure.persistence.ChargingSavingsQueryRepository.WeightedPrice;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Die beiden Preisketten der Heimlade-Ersparnis.
 *
 * Beide liefern neben dem Preis immer die verwendete Stufe und die Stichprobengroesse,
 * damit die Kachel benennen kann, worauf die Zahl beruht - "deine 23 eigenen Ladungen"
 * ist eine andere Aussage als "Median in Deutschland".
 *
 * Bewusste Asymmetrie: der oeffentliche Preis faellt am Ende auf den Landes-Median
 * zurueck, weil das ein Fremdvergleich ist. Der Heimpreis tut das nicht - ueber die
 * eigenen Stromkosten wird nichts behauptet, was der Nutzer nicht selbst hinterlegt hat.
 */
class ChargingPriceResolverTest {

    private static BigDecimal eur(String s) { return new BigDecimal(s); }

    // ---------------------------------------------------------------- Heimpreis

    /**
     * Gewichtet, nicht Median: ein 2-kWh-Log darf nicht so schwer wiegen wie ein
     * 60-kWh-Log. Was zaehlt, ist Summe Kosten durch Summe kWh - der Preis, den der
     * Nutzer ueber das Jahr tatsaechlich gezahlt hat.
     */
    @Test
    void homePrice_isTheWeightedAverage() {
        PriceBasis basis = ChargingPriceResolver.resolveHomePrice(
                new WeightedPrice(eur("0.2625"), 8));

        assertEquals(PriceSource.OWN_LOGS, basis.source());
        assertEquals(0, eur("0.2625").compareTo(basis.pricePerKwh()));
        assertEquals(8, basis.sampleSize());
    }

    /**
     * Ein einziges bepreistes Log genuegt. Eine hoehere Schwelle wuerde Nutzer
     * aussperren, die gerade erst anfangen ihre Preise zu erfassen.
     */
    @Test
    void homePrice_worksFromTheFirstPricedLog() {
        assertEquals(PriceSource.OWN_LOGS,
                ChargingPriceResolver.resolveHomePrice(new WeightedPrice(eur("0.30"), 1)).source());
    }

    /** Nulltarife sind echte Werte: PV-Ueberschuss kostet nichts. */
    @Test
    void homePrice_zeroIsAValidPrice() {
        PriceBasis basis = ChargingPriceResolver.resolveHomePrice(new WeightedPrice(BigDecimal.ZERO, 12));

        assertEquals(PriceSource.OWN_LOGS, basis.source());
        assertEquals(0, BigDecimal.ZERO.compareTo(basis.pricePerKwh()));
    }

    /** Ohne bepreiste Heimladung wird nicht geschaetzt - die Kachel zeigt ihren Leerzustand. */
    @Test
    void homePrice_withoutAnyPricedLog_isUnknown() {
        assertEquals(PriceSource.NONE, ChargingPriceResolver.resolveHomePrice(null).source());
        assertEquals(PriceSource.NONE, ChargingPriceResolver.resolveHomePrice(new WeightedPrice(null, 0)).source());
    }

    /** Ein unplausibler Durchschnitt deutet auf kaputte Daten und wird verworfen. */
    @Test
    void homePrice_implausibleAverage_isRejected() {
        assertEquals(PriceSource.NONE, ChargingPriceResolver.resolveHomePrice(new WeightedPrice(eur("9.99"), 5)).source());
    }

    // ------------------------------------------------------------ Oeffentlicher Preis

    @Test
    void publicPrice_fromOwnLogs_whenEnoughSamples() {
        List<BigDecimal> own = List.of(eur("0.39"), eur("0.42"), eur("0.45"), eur("0.51"), eur("0.36"));

        PriceBasis basis = ChargingPriceResolver.resolvePublicPrice(own, null, null);

        assertEquals(PriceSource.OWN_PUBLIC, basis.source());
        assertEquals(0, eur("0.42").compareTo(basis.pricePerKwh()));
        assertEquals(5, basis.sampleSize());
    }

    @Test
    void publicPrice_tooFewOwnLogs_fallsBackToRegion() {
        PriceBasis region = new PriceBasis(PriceSource.REGION, eur("0.44"), 18);

        PriceBasis basis = ChargingPriceResolver.resolvePublicPrice(
                List.of(eur("0.39"), eur("0.42")), () -> region,
                () -> new PriceBasis(PriceSource.COUNTRY, eur("0.40"), 2659));

        assertEquals(PriceSource.REGION, basis.source());
        assertEquals(0, eur("0.44").compareTo(basis.pricePerKwh()));
    }

    /**
     * Die Regionsstufe faellt durch, wenn sie zu duenn ist. Auf Prod erfuellt bei
     * Geohash-5 genau eine Zelle das Mindestmass - die Stufe muss also im Regelfall
     * geraeuschlos durchfallen, nicht eine Zahl aus drei Ladungen liefern.
     */
    @Test
    void publicPrice_thinRegion_fallsThroughToCountry() {
        PriceBasis basis = ChargingPriceResolver.resolvePublicPrice(
                List.of(), null, () -> new PriceBasis(PriceSource.COUNTRY, eur("0.40"), 2659));

        assertEquals(PriceSource.COUNTRY, basis.source());
        assertEquals(0, eur("0.40").compareTo(basis.pricePerKwh()));
        assertEquals(2659, basis.sampleSize());
    }

    @Test
    void publicPrice_withoutAnySource_isUnknown() {
        PriceBasis basis = ChargingPriceResolver.resolvePublicPrice(List.of(), null, null);

        assertEquals(PriceSource.NONE, basis.source());
    }

    // ------------------------------------------------------------ Plausibilitaet

    /**
     * Ausreisser fliegen raus, bevor der Median gebildet wird. Auf Prod gibt es
     * Ladungen zu 0,868 EUR/kWh ebenso wie Rechenartefakte nahe null.
     */
    @Test
    void implausiblePrices_areIgnored() {
        List<BigDecimal> own = List.of(
                eur("0.40"), eur("0.42"), eur("0.44"), eur("0.46"), eur("0.48"),
                eur("9.99"),   // Tippfehler
                eur("0.001")); // Rechenartefakt

        PriceBasis basis = ChargingPriceResolver.resolvePublicPrice(own, null, null);

        assertEquals(5, basis.sampleSize(), "nur die plausiblen Werte zaehlen");
        assertEquals(0, eur("0.44").compareTo(basis.pricePerKwh()));
    }

    /** Der Nulltarif ist beim Heimpreis plausibel - beim oeffentlichen Preis nicht. */
    @Test
    void zeroPrice_plausibleAtHome_notInPublic() {
        assertEquals(PriceSource.NONE, ChargingPriceResolver
                .resolvePublicPrice(List.of(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO), null, null).source());
    }
}
