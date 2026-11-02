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

    /** Unter 10 Prozent Unterschied zum Vormonat lohnt kein Satz. */
    private static final BigDecimal MIN_CHANGE_SHARE = new BigDecimal("0.10");

    /** logged_at steht in UTC, die Mail zeigt Ortszeit. */
    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Europe/Berlin");

    public record Rendered(String subject, String html) {}

    private MonthlyRecapMail() {
    }

    /**
     * @param priceUrl Einstieg "Preise nachtragen" in der App, steht nur im Hinweis auf fehlende Preise
     */
    /**
     * @param priceUrl Einstieg "Preise nachtragen" in der App, steht nur im Hinweis auf fehlende Preise
     */
    public static Rendered render(MonthlyRecap recap, RecapHighlights highlights, String unsubscribeUrl, String priceUrl) {
        boolean en = isEnglish(recap.locale());
        Locale locale = en ? Locale.ENGLISH : Locale.GERMAN;
        String month = monthName(recap.month(), locale);

        Map<String, String> vars = new HashMap<>();
        Set<String> sections = new HashSet<>();
        vars.put("username", recap.username());
        vars.put("month", month);
        vars.put("carName", recap.carName());
        vars.put("charges", String.valueOf(recap.charges()));
        vars.put("kwh", number(recap.kwh(), "#,##0.#", en));
        vars.put("unsubscribeUrl", unsubscribeUrl);
        vars.put("priceUrl", priceUrl);

        addHeader(recap, en, locale, sections, vars);
        addSavings(recap, en, sections, vars);
        addTiles(recap, en, sections, vars);
        addDetails(recap, en, sections, vars);
        addHighlights(highlights, en, sections, vars);
        addHint(recap, en, locale, sections, vars);
        // Vorschautext in der Inbox: der Hero-Satz und, wenn vorhanden, die Ersparnis.
        String saved = recap.savingsEur() == null ? ""
                : en ? " " + vars.get("savings") + " saved compared to a combustion car."
                : " Gegenüber einem Verbrenner " + vars.get("savings") + " gespart.";
        vars.put("preheader", vars.get("hero") + saved);

        String subject = en
                ? "Your " + month + " with the " + recap.carName()
                : "Dein " + month + " mit dem " + recap.carName();
        return new Rendered(subject, EmailTemplateRenderer.render(TEMPLATE, en ? "en" : "de", vars, sections));
    }

    /**
     * Hero wie in der Story: "August: 1.766 km für 147 €.", ohne Strecke "147 € für 12 Ladungen", ohne
     * Preise "1.766 km, 12 Ladungen", ohne beides nur die Ladungen. Darunter der Vormonat nur bei einer
     * Änderung, die man spürt.
     */
    private static void addHeader(MonthlyRecap recap, boolean en, Locale locale, Set<String> sections, Map<String, String> vars) {
        String month = vars.get("month");
        // Geschützte Leerzeichen: Zahl und Einheit bleiben im Hero zusammen (wie keepUnits in der Story).
        String km = recap.distanceKm() == null ? null : number(recap.distanceKm(), "#,##0", en) + " km";
        // Unter 0,50 € stünde "0 €" im Hero: dann die Variante ohne Preise.
        boolean hasCost = recap.costEur() != null && recap.costEur().setScale(0, RoundingMode.HALF_UP).signum() > 0;
        String cost = hasCost ? money(recap.costEur(), "#,##0", en).replace(" €", " €") : null;
        String charges = en
                ? recap.charges() + (recap.charges() == 1 ? " session" : " sessions")
                : recap.charges() + (recap.charges() == 1 ? " Ladung" : " Ladungen");
        String forWord = en ? " for " : " für ";
        String hero;
        if (km != null && cost != null) hero = month + ": " + km + forWord + cost + ".";
        else if (cost != null) hero = month + ": " + cost + forWord + charges + ".";
        else if (km != null) hero = month + ": " + km + ", " + charges + ".";
        else hero = month + ": " + charges + ".";
        vars.put("hero", hero);
        if (km == null) {
            return;
        }
        vars.put("km", km);
        BigDecimal previous = recap.previousDistanceKm();
        if (previous == null) {
            return;
        }
        BigDecimal diff = recap.distanceKm().subtract(previous);
        if (diff.abs().compareTo(previous.multiply(MIN_CHANGE_SHARE)) < 0) {
            return;
        }
        sections.add("kmChange");
        sections.add(diff.signum() > 0 ? "more" : "less");
        vars.put("kmDiff", number(diff.abs(), "#,##0", en) + " km");
        vars.put("prevMonth", monthName(recap.month().minusMonths(1), locale));
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

    /** Ersparnis als Hero, nur wenn Strom wirklich günstiger war. Nie die Stromkosten allein groß. */
    private static void addSavings(MonthlyRecap recap, boolean en, Set<String> sections, Map<String, String> vars) {
        BigDecimal savings = recap.savingsEur();
        if (savings == null) {
            return;
        }
        sections.add("savings");
        if (recap.pricelessHint() != null) {
            sections.add("partial");
        }
        vars.put("savings", money(savings, "#,##0.00", en));
        vars.put("cost", money(recap.costEur(), "#,##0.00", en));
        vars.put("fuelCost", money(recap.fuelCostEur(), "#,##0", en));
        vars.put("fuelPrice", money(recap.fuelPricePerLiter(), "#,##0.00", en));
        vars.put("fuelLiters", number(recap.fuelLitersPer100Km(), "#,##0.0", en));
        vars.put("carClass", en ? englishClassName(recap.carCategory()) : recap.carCategory().getDisplayName());
        int bar = recap.costEur().multiply(BigDecimal.valueOf(100))
                .divide(recap.fuelCostEur(), 0, RoundingMode.HALF_UP).intValue();
        vars.put("costBar", String.valueOf(Math.max(bar, 2)));
    }

    private static void addTiles(MonthlyRecap recap, boolean en, Set<String> sections, Map<String, String> vars) {
        if (recap.consumptionKwhPer100km() != null) {
            sections.add("consumption");
            vars.put("consumption", number(recap.consumptionKwhPer100km(), "#,##0.0", en));
        }
        if (recap.costPer100Km() != null) {
            sections.add("costPer100");
            vars.put("costPer100", money(recap.costPer100Km(), "#,##0.00", en));
        } else if (recap.costEur() != null) {
            sections.add("costTotal");
            vars.put("costTotal", money(recap.costEur(), "#,##0.00", en));
        }
    }

    /** Ein Satz unter den Kacheln: Schnelllader-Anteil und Heimanteil, soweit es sie gibt. */
    private static void addDetails(MonthlyRecap recap, boolean en, Set<String> sections, Map<String, String> vars) {
        if (recap.dcCharges() > 0) {
            sections.add("details");
            sections.add(recap.dcCharges() == recap.charges() ? "dcAll" : "dcSome");
            vars.put("dc", String.valueOf(recap.dcCharges()));
        }
        if (recap.homeSharePercent() != null) {
            sections.add("details");
            sections.add("home");
            vars.put("homeShare", recap.homeSharePercent() + (en ? "%" : " %"));
        }
    }

    /**
     * Platz in der Rangliste und Modellvergleich, wie in der Rückblick-Karte der Story. Die Plätze
     * kommen als ein Satz ("Platz 2 bei Längste Strecke · Platz 3 bei Meiste kWh"), der Vergleich
     * als zwei Zahlen; das Template setzt den Satz dazu.
     */
    private static void addHighlights(RecapHighlights highlights, boolean en, Set<String> sections, Map<String, String> vars) {
        if (highlights == null) return;
        if (!highlights.places().isEmpty()) {
            sections.add("highlights");
            sections.add("rank");
            StringBuilder line = new StringBuilder();
            for (RecapHighlights.Place p : highlights.places()) {
                if (!line.isEmpty()) line.append(" · ");
                line.append(en ? "Rank " + p.rank() + " in " + englishCategory(p.category())
                        : "Platz " + p.rank() + " bei " + p.category().getDisplayName());
            }
            vars.put("rankLine", line.toString());
        }
        if (highlights.peer() != null) {
            sections.add("highlights");
            sections.add("peer");
            vars.put("peerMine", number(highlights.peer().mine(), "0.0", en));
            vars.put("peerPeers", number(highlights.peer().peers(), "0.0", en));
        }
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
