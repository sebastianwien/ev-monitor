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

    /** Ihle, August 2026: 147,37 € Strom, 276,28 € Benzin, 1.766 km, Juli 1.700 km (kaum Änderung). */
    private Recap ihle() {
        return new Recap();
    }

    @Test
    void german_isANudge_heroOneHookAndTheButton() {
        MonthlyRecapMail.Rendered mail = render(ihle());

        assertThat(mail.subject()).isEqualTo("Wie war dein August mit dem Model 3?");
        assertThat(mail.html())
                .contains("August: 1.766\u00a0km für 147\u00a0€.")
                .contains("Gegenüber einem Verbrenner hast du <b class=\"save\" style=\"color: #15803d;\">129\u00a0€</b> gespart.*")
                .contains("Alles Weitere zu deinem August habe ich dir in der App zusammengestellt.")
                .contains("Meinen August ansehen")
                .contains("href=\"" + STORY_URL + "\"")
                .contains("* Vergleich mit einem Benziner der Mittelklasse: 7,6 l/100 km, Benzin 2,24 €/l laut Tankerkönig.")
                .contains("Diese Mail kommt einmal im Monat.")
                .contains(UNSUBSCRIBE)
                .doesNotContain("Hallo")
                .doesNotContain("kWh")
                .doesNotContain("Schnelllader")
                .doesNotContain("pro 100 km")
                .doesNotContain("{{")
                .doesNotContain("<!--if:")
                .doesNotContain("Preis nachtragen");
    }

    @Test
    void inviteFollowsTheHookDirectly_buttonRightBelow() {
        String html = render(ihle()).html();
        int hook = html.indexOf("gespart.*");
        int invite = html.indexOf("Alles Weitere zu deinem August");
        int button = html.indexOf("Meinen August ansehen");
        int bye = html.indexOf("Bis nächsten Monat");

        assertThat(hook).isPositive().isLessThan(invite);
        assertThat(invite).isLessThan(button);
        assertThat(button).isLessThan(bye);
        assertThat(html.substring(hook, invite)).doesNotContain("<div");
    }

    @Test
    void preheader_answersTheQuestionWithTheHero() {
        assertThat(render(ihle()).html()).contains(">August: 1.766\u00a0km für 147\u00a0€.</div>");
    }

    @Test
    void rankBeatsEverything_bestPlaceOnly() {
        RecapHighlights h = new RecapHighlights(List.of(
                new Place(LeaderboardCategory.MONTHLY_KWH, 3), new Place(LeaderboardCategory.MONTHLY_DISTANCE, 2)), null);
        Recap r = ihle();
        r.previousKm = "863";

        String html = render(r, h).html();

        assertThat(html).contains("Platz <b>2</b> bei Längste Strecke im August.")
                .doesNotContain("Meiste kWh").doesNotContain("als im Juli").doesNotContain("gespart").doesNotContain("Tankerkönig");
    }

    @Test
    void notableDistanceChange_beatsSavings() {
        Recap r = ihle();
        r.previousKm = "863";

        assertThat(render(r).html()).contains("<b>903\u00a0km</b> mehr gefahren als im Juli.").doesNotContain("gespart");
    }

    @Test
    void lessThanPreviousMonth_saysSo() {
        Recap r = ihle();
        r.previousKm = "2500";

        assertThat(render(r).html()).contains("<b>734\u00a0km</b> weniger gefahren als im Juli.");
    }

    @Test
    void changeBelowAQuarter_isNoNews_savingsTakeOver() {
        Recap r = ihle();
        r.previousKm = "1450";

        assertThat(render(r).html()).doesNotContain("als im Juli").contains("gespart");
    }

    @Test
    void electricityMoreExpensiveThanPetrol_noHookAtAll() {
        Recap r = ihle();
        r.cost = "300.00";

        String html = render(r).html();

        assertThat(html).doesNotContain("gespart").doesNotContain("Tankerkönig").contains("1.766\u00a0km").contains("Meinen August ansehen");
    }

    @Test
    void savingsBelowHalfAEuro_wouldRoundToZero_soNoSavingsSentence() {
        Recap r = ihle();
        r.cost = "275.90";

        assertThat(render(r).html()).doesNotContain("gespart");
    }

    @Test
    void withoutDistance_heroNamesTheCharges() {
        Recap r = ihle();
        r.km = null;

        assertThat(render(r).html()).contains("August: 147\u00a0€ für 12 Ladungen.").doesNotContain("gespart");
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
    void noPriceAtAll_showsHint() {
        Recap r = ihle();
        r.cost = null;
        r.hint = new PricelessHint(12, LocalDateTime.of(2026, 8, 1, 9, 0), new BigDecimal("30"));

        assertThat(render(r).html())
                .contains("August: 1.766\u00a0km, 12 Ladungen.")
                .contains("Bei keiner Ladung ist ein Preis eingetragen")
                .doesNotContain("gespart");
    }

    @Test
    void english_questionSubjectAndHook() {
        Recap r = ihle();
        r.locale = "en-GB";

        MonthlyRecapMail.Rendered mail = render(r);

        assertThat(mail.subject()).isEqualTo("How was your August with the Model 3?");
        assertThat(mail.html()).contains("August: 1,766\u00a0km for €147.")
                .contains("you saved <b class=\"save\" style=\"color: #15803d;\">€129</b>.*")
                .contains("* Compared with a petrol mid-size: 7.6 l/100 km, petrol at ")
                .contains("Everything else about your August is waiting for you in the app.")
                .contains("See my August");

        r.previousKm = "863";
        assertThat(render(r).html()).contains("<b>903\u00a0km</b> more than in July.");
        assertThat(render(r, new RecapHighlights(List.of(new Place(LeaderboardCategory.MONTHLY_POWER_CHARGER, 1)), null)).html())
                .contains("Rank <b>1</b> in Fastest Charger in August.");
    }

    @Test
    void norwegianAndSwedishUsers_getEnglish_unknownLocaleGetsGerman() {
        for (String locale : new String[]{"nb", "sv-SE"}) {
            Recap r = ihle();
            r.locale = locale;
            assertThat(render(r).subject()).startsWith("How");
        }
        for (String locale : new String[]{null, "de-AT"}) {
            Recap r = ihle();
            r.locale = locale;
            assertThat(render(r).subject()).startsWith("Wie");
        }
    }

    @Test
    void hero_variantsWithoutCost() {
        Recap bare = ihle();
        bare.cost = null;
        bare.km = null;
        assertThat(render(bare).html()).contains("August: 12 Ladungen.");

        // Unter 0,50 € stünde "0 €" im Hero: dann die Variante ohne Preise.
        Recap cents = ihle();
        cents.cost = "0.30";
        assertThat(render(cents).html()).contains("August: 1.766\u00a0km, 12 Ladungen.").doesNotContain("0\u00a0€");
    }

    @Test
    void darkModeMakesTheSavingsGreenReadable() {
        assertThat(render(ihle()).html()).contains(".save { color: #4ade80 !important; }");
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
        String previousKm = "1700";
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
