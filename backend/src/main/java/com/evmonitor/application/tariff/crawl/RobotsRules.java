package com.evmonitor.application.tariff.crawl;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * robots.txt nach RFC 9309: die Gruppe, die den eigenen Namen nennt, sonst "*". Die längste
 * passende Regel gilt, bei Gleichstand Allow. Pfade mit "*" (beliebig) und "$" (Ende).
 */
public final class RobotsRules {

    private record Rule(boolean allow, String path, Pattern pattern) {}

    private final List<Rule> rules;

    private RobotsRules(List<Rule> rules) {
        this.rules = rules;
    }

    /** {@code agent} ist der Produktname ohne Version, z. B. "EV-Monitor-Tarifcheck". */
    public static RobotsRules parse(String robotsTxt, String agent) {
        if (robotsTxt == null || robotsTxt.isBlank()) return new RobotsRules(List.of());
        String me = agent.toLowerCase(Locale.ROOT);
        List<Rule> mine = null;
        List<Rule> star = null;
        List<String> groupAgents = new ArrayList<>();
        List<Rule> groupRules = new ArrayList<>();
        boolean inRules = false;
        for (String raw : robotsTxt.split("\\R")) {
            String line = raw.replaceFirst("#.*", "").trim();
            int colon = line.indexOf(':');
            if (colon < 0) continue;
            String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();
            if (key.equals("user-agent")) {
                if (inRules) {
                    if (groupAgents.contains(me) && mine == null) mine = groupRules;
                    if (groupAgents.contains("*") && star == null) star = groupRules;
                    groupAgents = new ArrayList<>();
                    groupRules = new ArrayList<>();
                    inRules = false;
                }
                groupAgents.add(value.toLowerCase(Locale.ROOT));
            } else if (key.equals("allow") || key.equals("disallow")) {
                inRules = true;
                if (!value.isEmpty()) groupRules.add(new Rule(key.equals("allow"), value, compile(value)));
            }
        }
        if (groupAgents.contains(me) && mine == null) mine = groupRules;
        if (groupAgents.contains("*") && star == null) star = groupRules;
        return new RobotsRules(mine != null ? mine : star != null ? star : List.of());
    }

    public boolean allows(String path) {
        Rule best = null;
        for (Rule r : rules) {
            if (!r.pattern().matcher(path).lookingAt()) continue;
            if (best == null || r.path().length() > best.path().length()
                    || (r.path().length() == best.path().length() && r.allow())) {
                best = r;
            }
        }
        return best == null || best.allow();
    }

    private static Pattern compile(String path) {
        boolean anchored = path.endsWith("$");
        String body = anchored ? path.substring(0, path.length() - 1) : path;
        StringBuilder regex = new StringBuilder();
        for (String part : body.split("\\*", -1)) {
            if (!regex.isEmpty()) regex.append(".*");
            regex.append(Pattern.quote(part));
        }
        if (anchored) regex.append("$");
        return Pattern.compile(regex.toString());
    }
}
