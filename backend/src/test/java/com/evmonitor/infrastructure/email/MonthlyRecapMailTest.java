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

    /** Ihle, August 2026: 147,37 € Strom, 276,28 € Benzin, 1.766 km, Juli 863 km. */
    private Recap ihle() {
        return new Recap();
    }

    @Test
    void german_savingsLeadInsteadOfTheBill() {
        MonthlyRecapMail.Rendered mail = render(ihle());

        assertThat(mail.subject()).isEqualTo("Dein August mit dem Model 3");
        assertThat(mail.html())
                .contains("Dein August mit dem Model 3")
                .contains("1.766 km, 903 km mehr als im Juli.")
                .contains("Gespart gegenüber einem Verbrenner")
                .contains("128,91 €")
                .contains("Strom <b>147,37 €</b>")
                .contains("rund 276 €")
                .contains("width=\"53%\"")
                .contains("351,4")
                .contains("16,8")
                .contains("8,34 €")
                .contains("10 von 12 Ladungen am Schnelllader.")
                .contains("2,24 €/l")
                .contains(UNSUBSCRIBE)
                .doesNotContain("{{")
                .doesNotContain("<!--if:")
                .doesNotContain("daheim")
                .doesNotContain("Preis nachtragen");
    }

    @Test
    void smallChangeToPreviousMonth_isNotWorthASentence() {
        Recap r = ihle();
        r.previousKm = "1700";

        assertThat(render(r).html()).contains("1.766 km.</p>").doesNotContain("als im Juli");
    }

    @Test
    void lessThanPreviousMonth_saysSo() {
        Recap r = ihle();
        r.previousKm = "2500";

        assertThat(render(r).html()).contains("1.766 km, 734 km weniger als im Juli.");
    }

    @Test
    void electricityMoreExpensiveThanPetrol_showsNoSavingsAndNoBigEuroAmount() {
        Recap r = ihle();
        r.cost = "300.00";

        String html = render(r).html();

        assertThat(html).doesNotContain("Gespart").doesNotContain("Benzin").doesNotContain("Tankerkönig");
        assertThat(html).contains("1.766 km");
    }

    @Test
    void withoutDistance_headerNamesTheCharges_andTotalCostIsATile() {
        Recap r = ihle();
        r.km = null;

        String html = render(r).html();

        assertThat(html)
                .contains("12 Ladungen, 351,4 kWh.")
                .contains("147,37")
                .doesNotContain("Gespart")
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
                .doesNotContain("Gespart");
    }

    @Test
    void homeShare_andOnlyAc_formTheDetailLine() {
        Recap r = ihle();
        r.ac = 12;
        r.dc = 0;
        r.home = 75;

        assertThat(render(r).html()).contains("75 % daheim geladen.").doesNotContain("Schnelllader");
    }

    @Test
    void allDc_saysAll() {
        Recap r = ihle();
        r.ac = 0;
        r.dc = 12;

        assertThat(render(r).html()).contains("Alle 12 Ladungen am Schnelllader.");
    }

    @Test
    void english_subjectNumbersAndComparison() {
        Recap r = ihle();
        r.locale = "en-GB";

        MonthlyRecapMail.Rendered mail = render(r);

        assertThat(mail.subject()).isEqualTo("Your August with the Model 3");
        assertThat(mail.html()).contains("€128.91").contains("1,766 km, 903 km more than in July.").contains("Hi Ihle,");
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
        assertThat(render(r).html()).contains("7,6 l/100 km Benzin für einen Verbrenner der Klasse Mittelklasse bei ");

        r.locale = "en";
        r.liters = "8.4";
        r.category = VehicleCategory.SUV;
        assertThat(render(r).html()).contains("8.4 l/100 km of petrol for a combustion car in the SUV class at ");
    }

    @Test
    void usernameIsEscaped() {
        Recap r = ihle();
        r.username = "<b>x</b>";

        assertThat(render(r).html()).contains("&lt;b&gt;x&lt;/b&gt;").doesNotContain("<b>x</b>");
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
                .contains("You: 15.2 kWh/100 km, other drivers of your model in August: 16.8.");
    }

    @Test
    void highlights_none_leavesNoEmptyParagraph() {
        assertThat(render(ihle(), RecapHighlights.NONE).html()).doesNotContain("Platz ").doesNotContain("deines Modells").doesNotContain("{{rankLine}}");
    }

    private MonthlyRecapMail.Rendered render(Recap r) {
        return render(r, RecapHighlights.NONE);
    }

    private MonthlyRecapMail.Rendered render(Recap r, RecapHighlights highlights) {
        return MonthlyRecapMail.render(r.build(), highlights, UNSUBSCRIBE, PRICE_URL);
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
