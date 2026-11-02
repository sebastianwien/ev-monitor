package com.evmonitor.infrastructure.email;

import com.evmonitor.application.recap.MonthlyRecap;
import com.evmonitor.application.recap.RecapHighlights;
import com.evmonitor.application.recap.RecapHighlights.PeerConsumption;
import com.evmonitor.application.recap.RecapHighlights.Place;
import com.evmonitor.domain.LeaderboardCategory;
import com.evmonitor.domain.VehicleCategory;
import com.evmonitor.application.recap.MonthlyRecap.PricelessHint;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MonthlyRecapMailTest {

    private static final String UNSUBSCRIBE = "https://ev-monitor.net/api/unsubscribe?token=abc";
    private static final String PRICE_URL = "https://ev-monitor.net/logs?car=c1&nachtragen=preis";
    private static final String STORY_URL = "https://ev-monitor.net/story?utm_campaign=monthly-recap";

    /** Ihle, August 2026: 147,37 € Strom, 276,28 € Benzin, 1.766 km, Juli 863 km. */
    private Recap ihle() {
        return new Recap();
    }

    @Test
    void german_monthAsStory_wholeEuros_noBarsNoGreeting() {
        MonthlyRecapMail.Rendered mail = render(ihle());

        assertThat(mail.subject()).isEqualTo("Dein August mit dem Model 3");
        assertThat(mail.html())
                .contains("August: 1.766\u00a0km für 147\u00a0€.")
                .contains("Im August bist du <b>1.766\u00a0km</b> gefahren, <b>903 km</b> mehr als im Juli.")
                .contains("Geladen hast du <b>351,4 kWh</b> in <b>12</b> Ladungen, <b>10</b> davon am Schnelllader.")
                .contains("Ein Verbrenner hätte für dieselbe Strecke rund <b>276 €</b> gekostet, du hast also ")
                .contains("129 €</b> gespart.*")
                .contains("16,8")
                .contains("8,34 €")
                .contains("2,24 €/l")
                .contains("Ganzen August ansehen")
                .contains(UNSUBSCRIBE)
                .doesNotContain("Hallo")
                .doesNotContain("128,91")
                .doesNotContain("width=\"53%\"")
                .doesNotContain("text-transform: uppercase")
                .doesNotContain("mindestens drei Ladungen")
                .doesNotContain("{{")
                .doesNotContain("<!--if:")
                .doesNotContain("daheim")
                .doesNotContain("Preis nachtragen");
    }

    @Test
    void smallChangeToPreviousMonth_isNotWorthASentence() {
        Recap r = ihle();
        r.previousKm = "1700";

        assertThat(render(r).html()).contains("August: 1.766\u00a0km für 147\u00a0€.").doesNotContain("als im Juli");
    }

    @Test
    void savingsBelowHalfAEuro_wouldRoundToZero_soNoSavingsSentence() {
        Recap r = ihle();
        r.cost = "275.90";

        assertThat(render(r).html()).doesNotContain("gespart").doesNotContain("Tankerkönig");
    }

    @Test
    void lessThanPreviousMonth_saysSo() {
        Recap r = ihle();
        r.previousKm = "2500";

        assertThat(render(r).html()).contains("<b>734 km</b> weniger als im Juli.");
    }

    @Test
    void electricityMoreExpensiveThanPetrol_showsNoSavingsAndNoBigEuroAmount() {
        Recap r = ihle();
        r.cost = "300.00";

        String html = render(r).html();

        assertThat(html).doesNotContain("gespart").doesNotContain("Benzin").doesNotContain("Tankerkönig");
        assertThat(html).contains("1.766\u00a0km");
    }

    @Test
    void withoutDistance_headerNamesTheCharges_andTotalCostIsATile() {
        Recap r = ihle();
        r.km = null;

        String html = render(r).html();

        assertThat(html)
                .contains("August: 147\u00a0€ für 12 Ladungen.")
                .contains("Geladen hast du <b>351,4 kWh</b>")
                .doesNotContain("bist du")
                .contains("147,37")
                .doesNotContain("gespart")
                .doesNotContain("pro 100 km</p>");
    }

    @Test
    void onePricelessCharge_showsBerlinTimeAndButton() {
        Recap r = ihle();
        r.hint = new PricelessHint(1, LocalDateTime.of(2026, 8, 16, 11, 9), new BigDecimal("6.1"));

        assertThat(render(r).html())
                .contains("Bei einer Ladung fehlt der Preis: 16. August, 13:09 Uhr, 6,1 kWh.")
                .contains("href=\"https://ev-monitor.net/logs?car=c1&amp;nachtragen=preis\"")
                .contains("Preis nachtragen");
    }

    @Test
    void savingsWithPricelessCharges_footnoteSaysTheyAreLeftOut() {
        Recap r = ihle();
        r.hint = new PricelessHint(1, LocalDateTime.of(2026, 8, 16, 11, 9), new BigDecimal("6.1"));

        assertThat(render(r).html()).contains("* Nur Ladungen mit Preis, die Strecke anteilig nach kWh.");
        assertThat(render(ihle()).html()).doesNotContain("Nur Ladungen mit Preis");
    }

    @Test
    void noPriceAtAll_showsChargesAndHint() {
        Recap r = ihle();
        r.cost = null;
        r.hint = new PricelessHint(12, LocalDateTime.of(2026, 8, 1, 9, 0), new BigDecimal("30"));

        assertThat(render(r).html())
                .contains("Bei keiner Ladung ist ein Preis eingetragen")
                .contains("Preise nachtragen")
                .doesNotContain("gespart");
    }

    @Test
    void homeShare_andOnlyAc_formTheDetailLine() {
        Recap r = ihle();
        r.ac = 12;
        r.dc = 0;
        r.home = 75;

        assertThat(render(r).html()).contains("Davon <b>75 %</b> daheim.").doesNotContain("Schnelllader");
    }

    @Test
    void allDc_saysAll() {
        Recap r = ihle();
        r.ac = 0;
        r.dc = 12;

        assertThat(render(r).html()).contains("in <b>12</b> Ladungen, alle am Schnelllader.");
    }

    @Test
    void english_subjectNumbersAndComparison() {
        Recap r = ihle();
        r.locale = "en-GB";

        MonthlyRecapMail.Rendered mail = render(r);

        assertThat(mail.subject()).isEqualTo("Your August with the Model 3");
        assertThat(mail.html()).contains("so you saved <b style=\"color: #15803d;\">€129</b>.*").contains("August: 1,766\u00a0km for €147.")
                .contains("In August you drove <b>1,766\u00a0km</b>, <b>903 km</b> more than in July.")
                .contains("See all of August").doesNotContain("Hi Ihle");
    }

    @Test
    void norwegianAndSwedishUsers_getEnglish_unknownLocaleGetsGerman() {
        for (String locale : new String[]{"nb", "sv-SE"}) {
            Recap r = ihle();
            r.locale = locale;
            assertThat(render(r).subject()).startsWith("Your");
        }
        for (String locale : new String[]{null, "de-AT"}) {
            Recap r = ihle();
            r.locale = locale;
            assertThat(render(r).subject()).startsWith("Dein");
        }
    }

    @Test
    void footnote_namesTheLitresAndTheClassOfTheCar() {
        Recap r = ihle();
        assertThat(render(r).html()).contains("* Vergleich mit einem Benziner der Mittelklasse: 7,6 l/100 km, Benzin ");

        r.locale = "en";
        r.liters = "8.4";
        r.category = VehicleCategory.SUV;
        assertThat(render(r).html()).contains("* Compared with a petrol SUV: 8.4 l/100 km, petrol at ");
    }

    @Test
    void hero_followsTheStory_andFeedsThePreheader() {
        Recap noCost = ihle();
        noCost.cost = null;
        assertThat(render(noCost).html()).contains("August: 1.766\u00a0km, 12 Ladungen.").doesNotContain("für 147");

        Recap bare = ihle();
        bare.cost = null;
        bare.km = null;
        assertThat(render(bare).html()).contains("August: 12 Ladungen.");

        // Unter 0,50 € stünde "0 €" im Hero: dann die Variante ohne Preise.
        Recap cents = ihle();
        cents.cost = "0.30";
        assertThat(render(cents).html()).contains("August: 1.766 km, 12 Ladungen.").doesNotContain("0 €");

        Recap en = ihle();
        en.locale = "en";
        assertThat(render(en).html()).contains("August: 1,766 km for €147. €129 saved compared to a combustion car.</div>");

        String html = render(ihle()).html();
        assertThat(html).contains("August: 1.766\u00a0km für 147\u00a0€. Gegenüber einem Verbrenner 129 € gespart.</div>");
        assertThat(html).contains("<meta name=\"color-scheme\" content=\"light dark\">");
    }

    @Test
    void highlights_rankLineInGerman_podiumPlacesJoined() {
        RecapHighlights h = new RecapHighlights(List.of(
                new Place(LeaderboardCategory.MONTHLY_DISTANCE, 2), new Place(LeaderboardCategory.MONTHLY_KWH, 3)), null);

        String html = render(ihle(), h).html();

        assertThat(html).contains("Platz 2 bei Längste Strecke · Platz 3 bei Meiste kWh.").doesNotContain("andere Fahrer deines Modells");
    }

    @Test
    void highlights_peerLineInEnglish_withOneDecimalAndTheMonth() {
        Recap r = ihle();
        r.locale = "en";
        RecapHighlights h = new RecapHighlights(List.of(new Place(LeaderboardCategory.MONTHLY_POWER_CHARGER, 7)),
                new PeerConsumption(new BigDecimal("15.24"), new BigDecimal("16.8")));

        String html = render(r, h).html();

        assertThat(html).contains("Rank 7 in Fastest Charger.")
                .contains("You: <b>15.2 kWh/100 km</b>, other drivers of your model in August: <b>16.8</b>.");
    }

    @Test
    void highlights_none_leavesNoEmptyParagraph() {
        assertThat(render(ihle(), RecapHighlights.NONE).html()).doesNotContain("Platz ").doesNotContain("deines Modells").doesNotContain("{{rankLine}}");
    }

    private MonthlyRecapMail.Rendered render(Recap r) {
        return render(r, RecapHighlights.NONE);
    }

    private MonthlyRecapMail.Rendered render(Recap r, RecapHighlights highlights) {
        return MonthlyRecapMail.render(r.build(), highlights, UNSUBSCRIBE, PRICE_URL, STORY_URL);
    }

    /** Veränderbarer Baukasten, damit jeder Test nur sein Feld setzt. */
    private static class Recap {
        String locale = "de";
        String username = "Ihle";
        String cost = "147.37";
        String km = "1766";
        String previousKm = "863";
        int ac = 2;
        int dc = 10;
        Integer home = null;
        PricelessHint hint = null;
        String liters = "7.6";
        VehicleCategory category = VehicleCategory.SEDAN;

        MonthlyRecap build() {
            BigDecimal distance = km == null ? null : new BigDecimal(km);
            BigDecimal fuelPrice = new BigDecimal("2.235");
            BigDecimal fuelCost = distance == null ? null : new BigDecimal("276.28");
            return new MonthlyRecap(UUID.randomUUID(), UUID.randomUUID(), "ihle@example.com", username, locale,
                    YearMonth.of(2026, 8), "Model 3", 12, ac, dc, new BigDecimal("351.43"),
                    cost == null ? null : new BigDecimal(cost), distance,
                    distance == null ? null : new BigDecimal("16.81"), home, fuelCost, fuelPrice,
                    new BigDecimal(liters), category, hint,
                    previousKm == null ? null : new BigDecimal(previousKm));
        }
    }
}
