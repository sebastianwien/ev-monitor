package com.evmonitor.application.seo;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Such- und KI-Crawler, wie sie im User-Agent stehen (Teilstring, ohne Groß/klein). Reihenfolge zählt:
 * der erste Treffer gewinnt. Der eigene SEO-Audit gibt sich als Googlebot aus und wird vorher aussortiert.
 */
public enum CrawlerBot {
    GPTBOT("GPTBot (OpenAI, Training)", true, "gptbot"),
    OAI_SEARCHBOT("OAI-SearchBot (ChatGPT-Suche)", true, "oai-searchbot"),
    CHATGPT_USER("ChatGPT-User (Abruf aus dem Chat)", true, "chatgpt-user"),
    CLAUDE("Claude (Anthropic)", true, "claudebot", "claude-user", "claude-searchbot", "anthropic-ai"),
    PERPLEXITY("Perplexity", true, "perplexitybot", "perplexity-user"),
    COHERE("Cohere", true, "cohere-ai"),
    GOOGLEBOT("Googlebot", false, "googlebot", "google-inspectiontool"),
    BINGBOT("Bingbot (auch Copilot)", false, "bingbot"),
    APPLEBOT("Applebot (Siri, Spotlight)", false, "applebot"),
    OTHER_SEARCH("Andere Suchmaschinen", false, "yandexbot", "duckduckbot", "slurp"),
    SEO_TOOL("SEO-Tools", false, "ahrefsbot", "semrushbot"),
    SOCIAL("Link-Vorschauen (Social, Messenger)", false, "facebookexternalhit", "twitterbot", "linkedinbot", "whatsapp",
            "discordbot", "redditbot", "slackbot", "telegrambot", "mastodon");

    /** Kennung im User-Agent von {@link SeoAuditProperties#getUserAgent()}. */
    private static final String OWN_AUDIT = "ev-monitor-seo-audit";

    private final String label;
    private final boolean ai;
    private final List<String> markers;

    CrawlerBot(String label, boolean ai, String... markers) {
        this.label = label;
        this.ai = ai;
        this.markers = List.of(markers);
    }

    public String getLabel() {
        return label;
    }

    public boolean isAi() {
        return ai;
    }

    public static Optional<CrawlerBot> of(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) return Optional.empty();
        String ua = userAgent.toLowerCase(Locale.ROOT);
        if (ua.contains(OWN_AUDIT)) return Optional.empty();
        return Arrays.stream(values()).filter(b -> b.markers.stream().anyMatch(ua::contains)).findFirst();
    }
}
