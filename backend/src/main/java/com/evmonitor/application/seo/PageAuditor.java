package com.evmonitor.application.seo;

import com.evmonitor.application.seo.SeoAudit.Check;
import com.evmonitor.application.seo.SeoAudit.Finding;
import com.evmonitor.application.seo.SeoAudit.PageReport;
import com.evmonitor.application.seo.SeoAudit.Severity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Prüft eine gerenderte Seite so, wie Googlebot sie ohne JS bekommt. Reine Funktion ohne I/O; Link-Befunde
 * (kaputt, verwaist) brauchen alle Seiten und entstehen in {@link SeoAuditService}.
 */
@Component
public class PageAuditor {

    private static final Pattern CONSUMPTION = Pattern.compile("\\d+,\\d\\s?kWh/100\\s?km");
    private static final Pattern IMPERIAL = Pattern.compile("mi/kWh|\\bmiles\\b|\\$\\s?\\d|\\d\\s?\\$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ASSET = Pattern.compile("\\.(png|jpe?g|svg|webp|gif|ico|pdf|css|js|xml|txt|json)$", Pattern.CASE_INSENSITIVE);

    private enum PageType { LIST, BRAND, MODEL, OTHER }

    private final SeoAuditProperties properties;
    private final ObjectMapper objectMapper;

    public PageAuditor(SeoAuditProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /** status 0 = nicht erreichbar (Timeout, Verbindung, zu groß). */
    public PageReport audit(String path, int status, String html) {
        List<Finding> findings = new ArrayList<>();
        if (status != 200 || html == null) {
            findings.add(error(Check.STATUS, status == 0 ? "Seite nicht erreichbar" : "HTTP " + status + " statt 200"));
            return new PageReport(path, status, false, findings, Set.of());
        }
        Document doc = Jsoup.parse(html, "https://" + properties.getSiteHost() + path);

        boolean noindex = doc.select("meta[name=robots]").stream()
                .anyMatch(m -> m.attr("content").toLowerCase(Locale.ROOT).contains("noindex"));
        if (noindex) findings.add(error(Check.NOINDEX, "Seite steht in der Sitemap, trägt aber noindex"));

        checkCanonical(doc, path, findings);
        checkTitle(doc, findings);
        checkDescription(doc, findings);
        int h1 = doc.select("h1").size();
        if (h1 != 1) findings.add(warning(Check.H1, h1 == 0 ? "Kein H1" : h1 + " H1 statt genau einem"));
        PageType type = pageType(path);
        checkJsonLd(doc, type, findings);
        if (type == PageType.MODEL) checkPrerender(doc, path, findings);

        return new PageReport(path, status, noindex, findings, internalLinks(doc, path));
    }

    private void checkCanonical(Document doc, String path, List<Finding> findings) {
        Elements canonicals = doc.select("link[rel=canonical]");
        if (canonicals.isEmpty()) {
            findings.add(error(Check.CANONICAL, "Kein Canonical"));
            return;
        }
        if (canonicals.size() > 1) {
            findings.add(error(Check.CANONICAL, canonicals.size() + " Canonicals statt einem"));
            return;
        }
        String href = canonicals.first().attr("href").trim();
        if (!sameHost(href) || !SeoPath.normalize(href).equals(path)) {
            findings.add(error(Check.CANONICAL, "Canonical zeigt auf " + href));
        }
    }

    private void checkTitle(Document doc, List<Finding> findings) {
        String title = doc.title().trim();
        if (title.isEmpty()) {
            findings.add(error(Check.TITLE, "Kein Title"));
        } else if (title.length() < properties.getTitleMin() || title.length() > properties.getTitleMax()) {
            findings.add(warning(Check.TITLE, "Title hat " + title.length() + " Zeichen (Ziel " + properties.getTitleMin()
                    + "-" + properties.getTitleMax() + "): " + title));
        }
    }

    private void checkDescription(Document doc, List<Finding> findings) {
        Element meta = doc.selectFirst("meta[name=description]");
        String description = meta == null ? "" : meta.attr("content").trim();
        if (description.isEmpty()) {
            findings.add(error(Check.DESCRIPTION, "Keine Description"));
        } else if (description.length() < properties.getDescriptionMin() || description.length() > properties.getDescriptionMax()) {
            findings.add(warning(Check.DESCRIPTION, "Description hat " + description.length() + " Zeichen (Ziel "
                    + properties.getDescriptionMin() + "-" + properties.getDescriptionMax() + ")"));
        }
    }

    private void checkJsonLd(Document doc, PageType type, List<Finding> findings) {
        Set<String> types = new LinkedHashSet<>();
        int block = 0;
        for (Element script : doc.select("script[type=application/ld+json]")) {
            block++;
            try {
                collectTypes(objectMapper.readTree(script.data()), types);
            } catch (Exception e) {
                findings.add(error(Check.JSONLD, "JSON-LD-Block " + block + " ist kein gültiges JSON"));
            }
        }
        for (String required : requiredTypes(type)) {
            if (!types.contains(required)) findings.add(error(Check.JSONLD, "JSON-LD " + required + " fehlt"));
        }
    }

    /** Oberste Ebene, Arrays und @graph; verschachtelte Typen (ListItem, Answer) zählen nicht. */
    private static void collectTypes(JsonNode node, Set<String> types) {
        if (node == null) return;
        if (node.isArray()) {
            node.forEach(n -> collectTypes(n, types));
            return;
        }
        JsonNode type = node.get("@type");
        if (type != null && type.isTextual()) types.add(type.asText());
        if (type != null && type.isArray()) type.forEach(t -> types.add(t.asText()));
        collectTypes(node.get("@graph"), types);
    }

    private static List<String> requiredTypes(PageType type) {
        return switch (type) {
            case LIST -> List.of("ItemList");
            case BRAND -> List.of("BreadcrumbList", "ItemList");
            case MODEL -> List.of("BreadcrumbList", "Product");
            case OTHER -> List.of();
        };
    }

    /** Rückfallschutz für den Meilen-Bug: Modellseite muss ohne JS Modellname und Verbrauch in kWh/100 km zeigen. */
    private void checkPrerender(Document doc, String path, List<Finding> findings) {
        String model = path.substring(path.lastIndexOf('/') + 1).replace('_', ' ');
        Element h1 = doc.selectFirst("h1");
        if (h1 == null || !h1.text().toLowerCase(Locale.ROOT).contains(model.toLowerCase(Locale.ROOT))) {
            findings.add(error(Check.PRERENDER, "Modellname \"" + model + "\" fehlt im H1"));
        }
        String text = doc.body() == null ? "" : doc.body().text();
        if (!CONSUMPTION.matcher(text).find()) {
            findings.add(error(Check.PRERENDER, "Kein Verbrauchswert in kWh/100 km im HTML"));
        }
        var imperial = IMPERIAL.matcher(text);
        if (imperial.find()) {
            findings.add(error(Check.PRERENDER, "US-Einheit oder Dollar im HTML: \"" + imperial.group().trim() + "\""));
        }
    }

    private Set<String> internalLinks(Document doc, String self) {
        Set<String> links = new LinkedHashSet<>();
        for (Element a : doc.select("a[href]")) {
            String abs = a.absUrl("href");
            if (abs.isEmpty() || !sameHost(abs)) continue;
            String path = SeoPath.normalize(abs);
            if (path.equals(self) || path.startsWith("/api/") || ASSET.matcher(path).find()) continue;
            links.add(path);
        }
        return links;
    }

    private boolean sameHost(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            return host != null && ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    && host.equalsIgnoreCase(properties.getSiteHost());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static PageType pageType(String path) {
        if (path.equals("/modelle")) return PageType.LIST;
        if (!path.startsWith("/modelle/")) return PageType.OTHER;
        int depth = path.substring("/modelle/".length()).split("/").length;
        return depth == 1 ? PageType.BRAND : depth == 2 ? PageType.MODEL : PageType.OTHER;
    }

    private static Finding error(Check check, String message) {
        return new Finding(check, Severity.ERROR, message);
    }

    private static Finding warning(Check check, String message) {
        return new Finding(check, Severity.WARNING, message);
    }
}
