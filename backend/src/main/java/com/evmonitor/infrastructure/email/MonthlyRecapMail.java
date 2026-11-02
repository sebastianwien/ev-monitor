package com.evmonitor.infrastructure.email;

import com.evmonitor.application.recap.MonthlyRecap;
import com.evmonitor.application.recap.MonthlyRecap.PricelessHint;
import com.evmonitor.application.recap.RecapHighlights;
import com.evmonitor.domain.LeaderboardCategory;
import com.evmonitor.domain.VehicleCategory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.ZoneId;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Macht aus einem {@link MonthlyRecap} Betreff und HTML. Der Text steht vollständig in
 * {@code monthly-recap.html} (de, en); hier wird nur formatiert und entschieden, welche
 * Abschnitte stehen bleiben.
 */
public final class MonthlyRecapMail {

    static final String TEMPLATE = "monthly-recap.html";

    /**
     * Ab einem Viertel mehr oder weniger als im Vormonat ist die Strecke eine Neuigkeit. Darunter
     * schwankt sie fast jeden Monat, und der Aufhänger wäre jeden Monat derselbe.
     */
    private static final BigDecimal NOTABLE_CHANGE_SHARE = new BigDecimal("0.25");

    /** logged_at steht in UTC, die Mail zeigt Ortszeit. */
    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Europe/Berlin");

    public record Rendered(String subject, String html) {}

    private MonthlyRecapMail() {
    }

    /**
     * Die Mail ist ein Anstoß, kein Abzug der Story: Hero, ein Aufhänger, Knopf in die App. Alle
     * Kacheln stehen im Rückblick der Story.
     *
     * @param priceUrl Einstieg "Preise nachtragen" in der App, steht nur im Hinweis auf fehlende Preise
     * @param storyUrl Knopf "Ganzen Monat ansehen"
     */
    public static Rendered render(MonthlyRecap recap, RecapHighlights highlights, String unsubscribeUrl,
                                  String priceUrl, String storyUrl) {
        boolean en = isEnglish(recap.locale());
        Locale locale = en ? Locale.ENGLISH : Locale.GERMAN;
        String month = monthName(recap.month(), locale);

        Map<String, String> vars = new HashMap<>();
        Set<String> sections = new HashSet<>();
        vars.put("month", month);
        vars.put("unsubscribeUrl", unsubscribeUrl);
        vars.put("priceUrl", priceUrl);
        vars.put("storyUrl", storyUrl);

        vars.put("hero", hero(recap, en, month));
        // Genau ein Aufhänger, der stärkste zuerst: so liest sich die Mail nicht jeden Monat gleich.
        if (!addRankHook(highlights, en, sections, vars) && !addDistanceHook(recap, en, locale, sections, vars)) {
            addSavingsHook(recap, en, sections, vars);
        }
        addHint(recap, en, locale, sections, vars);
        // Der Betreff fragt, der Vorschautext in der Inbox antwortet mit dem Hero.
        vars.put("preheader", vars.get("hero"));

        String subject = en
                ? "How was your " + month + " with the " + recap.carName() + "?"
                : "Wie war dein " + month + " mit dem " + recap.carName() + "?";
        return new Rendered(subject, EmailTemplateRenderer.render(TEMPLATE, en ? "en" : "de", vars, sections));
    }

    /**
     * Wie in der Story: "August: 1.766 km für 147 €.", ohne Strecke "147 € für 12 Ladungen", ohne
     * Preise "1.766 km, 12 Ladungen", ohne beides nur die Ladungen.
     */
    private static String hero(MonthlyRecap recap, boolean en, String month) {
        // Geschützte Leerzeichen: Zahl und Einheit bleiben zusammen (wie keepUnits in der Story).
        String km = recap.distanceKm() == null ? null : number(recap.distanceKm(), "#,##0", en) + "\u00a0km";
        // Unter 0,50 € stünde "0 €" im Hero: dann die Variante ohne Preise.
        boolean hasCost = recap.costEur() != null && recap.costEur().setScale(0, RoundingMode.HALF_UP).signum() > 0;
        String cost = hasCost ? money(recap.costEur(), "#,##0", en).replace(" €", "\u00a0€") : null;
        String charges = en
                ? recap.charges() + (recap.charges() == 1 ? " session" : " sessions")
                : recap.charges() + (recap.charges() == 1 ? " Ladung" : " Ladungen");
        String forWord = en ? " for " : " für ";
        if (km != null && cost != null) return month + ": " + km + forWord + cost + ".";
        if (cost != null) return month + ": " + cost + forWord + charges + ".";
        if (km != null) return month + ": " + km + ", " + charges + ".";
        return month + ": " + charges + ".";
    }

    /** Aufhänger 1: der beste Platz in der Rangliste des Monats. */
    private static boolean addRankHook(RecapHighlights highlights, boolean en, Set<String> sections, Map<String, String> vars) {
        if (highlights == null || highlights.places().isEmpty()) {
            return false;
        }
        RecapHighlights.Place best = highlights.places().stream()
                .min(Comparator.comparingInt(RecapHighlights.Place::rank)).orElseThrow();
        sections.add("hookRank");
        vars.put("rank", String.valueOf(best.rank()));
        vars.put("category", en ? englishCategory(best.category()) : best.category().getDisplayName());
        return true;
    }

    /** Aufhänger 2: deutlich mehr oder weniger gefahren als im Vormonat. */
    private static boolean addDistanceHook(MonthlyRecap recap, boolean en, Locale locale, Set<String> sections, Map<String, String> vars) {
        BigDecimal previous = recap.previousDistanceKm();
        if (recap.distanceKm() == null || previous == null || previous.signum() <= 0) {
            return false;
        }
        BigDecimal diff = recap.distanceKm().subtract(previous);
        if (diff.abs().compareTo(previous.multiply(NOTABLE_CHANGE_SHARE)) < 0) {
            return false;
        }
        sections.add(diff.signum() > 0 ? "hookMore" : "hookLess");
        vars.put("kmDiff", number(diff.abs(), "#,##0", en) + "\u00a0km");
        vars.put("prevMonth", monthName(recap.month().minusMonths(1), locale));
        return true;
    }

    /**
     * Aufhänger 3: Ersparnis gegenüber dem Benziner, nur wenn Strom wirklich günstiger war. Ganze Euro:
     * der Benziner ist eine Schätzung, Cent täuschten Genauigkeit vor. Was auf 0 € rundet, entfällt.
     */
    private static boolean addSavingsHook(MonthlyRecap recap, boolean en, Set<String> sections, Map<String, String> vars) {
        BigDecimal savings = recap.savingsEur();
        if (savings == null || savings.setScale(0, RoundingMode.HALF_UP).signum() <= 0) {
            return false;
        }
        sections.add("savings");
        if (recap.pricelessHint() != null) {
            sections.add("partial");
        }
        vars.put("savings", money(savings, "#,##0", en).replace(" €", "\u00a0€"));
        vars.put("fuelPrice", money(recap.fuelPricePerLiter(), "#,##0.00", en));
        vars.put("fuelLiters", number(recap.fuelLitersPer100Km(), "#,##0.0", en));
        vars.put("carClass", en ? englishClassName(recap.carCategory()) : recap.carCategory().getDisplayName());
        return true;
    }

    /** VehicleCategory kennt nur deutsche Namen; die englische Mail braucht ihre eigenen. */
    private static String englishClassName(VehicleCategory category) {
        return switch (category) {
            case CITY_CAR -> "city car";
            case COMPACT -> "compact";
            case SEDAN -> "mid-size";
            case SUV -> "SUV";
            case LARGE_SUV -> "large SUV";
            case LUXURY -> "luxury";
            case SPORTS -> "sports car";
            case VAN -> "van";
            case PICKUP -> "pickup";
        };
    }

    /** Dieselben Namen wie in der Rangliste der App (leaderboard.cat_* in en.yaml). */
    private static String englishCategory(LeaderboardCategory category) {
        return switch (category) {
            case MONTHLY_KWH -> "Most energy charged";
            case MONTHLY_CHARGES -> "Charging sessions";
            case MONTHLY_DISTANCE -> "Longest distance";
            case MONTHLY_CHEAPEST -> "Cheapest charger";
            case MONTHLY_NIGHT_OWL -> "Night Owl";
            case MONTHLY_ICE_CHARGER -> "Ice Charger";
            case MONTHLY_HEAT_CHARGER -> "Sahara Session";
            case MONTHLY_POWER_CHARGER -> "Fastest Charger";
        };
    }

    /**
     * Deutsch für de-Locales und ohne Angabe, sonst Englisch. Anders als die übrigen Mails, die
     * alles außer en auf Deutsch schicken: nb- und sv-Nutzer lesen eher Englisch als Deutsch.
     */
    static boolean isEnglish(String locale) {
        return locale != null && !locale.isBlank() && !locale.toLowerCase().startsWith("de");
    }

    /** Höchstens ein Hinweis pro Mail, damit sie sich nicht wie eine Mängelliste liest. */
    private static void addHint(MonthlyRecap recap, boolean en, Locale locale, Set<String> sections, Map<String, String> vars) {
        PricelessHint hint = recap.pricelessHint();
        if (hint == null) {
            return;
        }
        if (recap.costEur() == null) {
            sections.add("hintAll");
        } else if (hint.count() == 1) {
            sections.add("hintOne");
            DateTimeFormatter format = DateTimeFormatter.ofPattern(en ? "d MMMM, HH:mm" : "d. MMMM, HH:mm 'Uhr'", locale);
            vars.put("hintAt", hint.firstAt().atOffset(ZoneOffset.UTC).atZoneSameInstant(DISPLAY_ZONE).format(format));
            if (hint.firstKwh() != null) {
                sections.add("hintKwh");
                vars.put("hintKwh", number(hint.firstKwh(), "#,##0.0", en) + " kWh");
            }
        } else {
            sections.add("hintMany");
            vars.put("hintCount", String.valueOf(hint.count()));
        }
    }

    private static String monthName(YearMonth month, Locale locale) {
        return month.getMonth().getDisplayName(TextStyle.FULL, locale);
    }

    private static String money(BigDecimal value, String pattern, boolean en) {
        String amount = number(value, pattern, en);
        return en ? "€" + amount : amount + " €";
    }

    private static String number(BigDecimal value, String pattern, boolean en) {
        return new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(en ? Locale.US : Locale.GERMANY)).format(value);
    }
}
