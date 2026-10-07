package com.evmonitor.infrastructure.email;

import com.evmonitor.application.recap.MonthlyRecap;
import com.evmonitor.application.recap.MonthlyRecap.PricelessHint;
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
    public static Rendered render(MonthlyRecap recap, String unsubscribeUrl, String priceUrl) {
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
        addHint(recap, en, locale, sections, vars);

        String subject = en
                ? "Your " + month + " with the " + recap.carName()
                : "Dein " + month + " mit dem " + recap.carName();
        return new Rendered(subject, EmailTemplateRenderer.render(TEMPLATE, en ? "en" : "de", vars, sections));
    }

    /** Unter dem Titel: die Strecke, und der Vormonat nur bei einer Änderung, die man spürt. */
    private static void addHeader(MonthlyRecap recap, boolean en, Locale locale, Set<String> sections, Map<String, String> vars) {
        if (recap.distanceKm() == null) {
            sections.add("noDistance");
            return;
        }
        sections.add("distance");
        vars.put("km", number(recap.distanceKm(), "#,##0", en) + " km");
        BigDecimal previous = recap.previousDistanceKm();
        if (previous == null) {
            return;
        }
        BigDecimal diff = recap.distanceKm().subtract(previous);
        if (diff.abs().compareTo(previous.multiply(MIN_CHANGE_SHARE)) < 0) {
            return;
        }
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
