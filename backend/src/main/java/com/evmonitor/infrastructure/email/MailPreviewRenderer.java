package com.evmonitor.infrastructure.email;

import com.evmonitor.application.mail.MailType;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Vorschau einer Mail für den Admin-Tab: das deutsche Template mit Beispieldaten statt echter Nutzerdaten.
 * Alle optionalen Abschnitte sind an, Links zeigen auf "#". Unbekannte Platzhalter erscheinen als [name].
 */
public final class MailPreviewRenderer {

    private static final Pattern VARIABLE = Pattern.compile("\\{\\{([a-zA-Z0-9]+)}}");
    private static final Pattern SECTION = Pattern.compile("<!--if:([a-zA-Z0-9]+)-->");

    private static final Map<String, String> SAMPLES = Map.ofEntries(
            Map.entry("username", "Max"),
            Map.entry("model", "VW ID.3"),
            Map.entry("vehicle", "Kia EV3"),
            Map.entry("code", "123456"),
            Map.entry("endsAt", "8. Dezember 2026"),
            Map.entry("imported", "42"),
            Map.entry("skipped", "3"),
            Map.entry("month", "Oktober"),
            Map.entry("prevMonth", "September"),
            Map.entry("reason", "Der Text enthält persönliche Daten (zum Beispiel Namen oder Kennzeichen)."),
            Map.entry("note", "Bitte ohne Kennzeichen."),
            Map.entry("mileage", "15.000 km im Jahr, Pendeln 40 km an 5 Tagen"),
            Map.entry("costBasis", "15.000 km im Jahr"),
            Map.entry("highwayPercent", "30"),
            Map.entry("longTripKm", "500"),
            Map.entry("winterRangeKm", "310"),
            Map.entry("summerRangeKm", "360"),
            Map.entry("travelTime", "6 h 20 min"),
            Map.entry("tripDetail", "inkl. 1 Ladestopp, grob 35 min · Ø 110 km/h"),
            Map.entry("chargesSummer", "alle 7 Tage"),
            Map.entry("chargesWinter", "alle 4 Tage"),
            Map.entry("monthlyCostEur", "58"),
            Map.entry("pricePerKwh", "0,30"));

    private MailPreviewRenderer() {
    }

    public static String render(MailType type) {
        String source = EmailTemplateRenderer.source(type.template(), "de");
        Map<String, String> vars = new HashMap<>();
        Matcher v = VARIABLE.matcher(source);
        while (v.find()) {
            String name = v.group(1);
            vars.put(name, name.endsWith("Url") ? "#" : SAMPLES.getOrDefault(name, "[" + name + "]"));
        }
        Set<String> sections = new LinkedHashSet<>();
        Matcher s = SECTION.matcher(source);
        while (s.find()) sections.add(s.group(1));
        return EmailTemplateRenderer.render(type.template(), "de", vars, sections);
    }
}
