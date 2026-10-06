package com.evmonitor.infrastructure.email;

import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Laedt HTML-Mail-Templates aus {@code email-templates/} und ersetzt {@code {{platzhalter}}}.
 * Alle Werte werden HTML-escaped; Templates tragen Struktur und Stil, Aufrufer liefern nur Text.
 *
 * <p>Optionale Abschnitte stehen als {@code <!--if:name-->...<!--end:name-->} im Template und
 * bleiben nur stehen, wenn der Aufrufer {@code name} einschaltet. So bleibt der ganze Text einer
 * Mail im Template, auch wenn Zeilen je nach Daten wegfallen.
 */
public final class EmailTemplateRenderer {

    private static final Pattern SECTION = Pattern.compile("<!--if:([a-zA-Z0-9]+)-->(.*?)<!--end:\\1-->", Pattern.DOTALL);

    private EmailTemplateRenderer() {
    }

    /**
     * Laedt {@code email-templates/{lang}/{templateName}}, Fallback {@code email-templates/{templateName}}.
     */
    public static String render(String templateName, String lang, Map<String, String> variables) {
        return render(templateName, lang, variables, Set.of());
    }

    public static String render(String templateName, String lang, Map<String, String> variables, Set<String> sections) {
        try {
            ClassPathResource localeResource = new ClassPathResource("email-templates/" + lang + "/" + templateName);
            ClassPathResource fallbackResource = new ClassPathResource("email-templates/" + templateName);

            ClassPathResource resource = localeResource.exists() ? localeResource : fallbackResource;
            String template = applySections(resource.getContentAsString(StandardCharsets.UTF_8), sections);

            for (Map.Entry<String, String> entry : variables.entrySet()) {
                template = template.replace("{{" + entry.getKey() + "}}", escapeHtml(entry.getValue()));
            }
            return template;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load email template: " + templateName, e);
        }
    }

    /** Von außen nach innen: ein ausgeschalteter Abschnitt nimmt seine inneren mit. */
    static String applySections(String template, Set<String> enabled) {
        String result = template;
        Matcher matcher = SECTION.matcher(result);
        while (matcher.find()) {
            String replacement = enabled.contains(matcher.group(1)) ? matcher.group(2) : "";
            result = result.substring(0, matcher.start()) + replacement + result.substring(matcher.end());
            matcher = SECTION.matcher(result);
        }
        return result;
    }

    static String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }
}
