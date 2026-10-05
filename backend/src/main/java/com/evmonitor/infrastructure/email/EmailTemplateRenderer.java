package com.evmonitor.infrastructure.email;

import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Laedt HTML-Mail-Templates aus {@code email-templates/} und ersetzt {@code {{platzhalter}}}.
 * Alle Werte werden HTML-escaped; Templates tragen Struktur und Stil, Aufrufer liefern nur Text.
 */
public final class EmailTemplateRenderer {

    private EmailTemplateRenderer() {
    }

    /**
     * Laedt {@code email-templates/{lang}/{templateName}}, Fallback {@code email-templates/{templateName}}.
     */
    public static String render(String templateName, String lang, Map<String, String> variables) {
        try {
            ClassPathResource localeResource = new ClassPathResource("email-templates/" + lang + "/" + templateName);
            ClassPathResource fallbackResource = new ClassPathResource("email-templates/" + templateName);

            ClassPathResource resource = localeResource.exists() ? localeResource : fallbackResource;
            String template = resource.getContentAsString(StandardCharsets.UTF_8);

            for (Map.Entry<String, String> entry : variables.entrySet()) {
                template = template.replace("{{" + entry.getKey() + "}}", escapeHtml(entry.getValue()));
            }
            return template;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load email template: " + templateName, e);
        }
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
