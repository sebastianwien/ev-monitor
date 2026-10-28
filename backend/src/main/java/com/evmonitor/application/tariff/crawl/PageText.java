package com.evmonitor.application.tariff.crawl;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Lesbarer Text einer Tarifseite: ein Block je Zeile, ohne Skripte und Styles. Versteckte
 * CMS-Listen bleiben drin (IONITY führt seine Länderpreise so). Preisbilder zählen über ihren
 * Alt-Text, damit eine neue Tarifkachel den Fingerabdruck ändert, auch wenn der Text gleich bleibt.
 *
 * @param text        Seitentext, Zeilen ohne doppelte Leerzeichen
 * @param priceImages "Alt-Text | absolute URL" für Bilder, deren Alt-Text nach Preis klingt
 */
public record PageText(String text, List<String> priceImages) {

    private static final Set<String> BLOCKS = Set.of("p", "div", "li", "tr", "td", "th", "h1", "h2", "h3", "h4", "h5", "h6",
            "br", "section", "article", "header", "footer", "dt", "dd", "table", "ul", "ol", "main", "aside", "nav");
    private static final Pattern PRICE_ALT = Pattern.compile("(?i)tarif|preis|€|\\bct\\b|kwh|grundgeb");

    public static PageText of(String html, String baseUrl) {
        Document doc = Jsoup.parse(html, baseUrl);
        doc.select("script, style, noscript, svg, template, iframe, head").remove();
        StringBuilder out = new StringBuilder();
        NodeTraversor.traverse(new NodeVisitor() {
            @Override
            public void head(Node node, int depth) {
                if (node instanceof TextNode t) out.append(t.text());
                else if (node instanceof Element e && BLOCKS.contains(e.normalName())) out.append('\n');
            }

            @Override
            public void tail(Node node, int depth) {
                if (node instanceof Element e && BLOCKS.contains(e.normalName())) out.append('\n');
            }
        }, doc.body() == null ? doc : doc.body());
        List<String> images = doc.select("img[alt]").stream()
                .filter(img -> PRICE_ALT.matcher(img.attr("alt")).find())
                .map(img -> img.attr("alt").trim().replaceAll("\\s+", " ") + " | " + img.absUrl("src"))
                .distinct()
                .toList();
        return new PageText(normalize(out.toString()), images);
    }

    /** SHA-256 über Text und Preisbilder; Leerraum zählt nicht. */
    public String fingerprint() {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update(text.getBytes(StandardCharsets.UTF_8));
            for (String image : priceImages) sha.update(("\n" + image).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(sha.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String normalize(String raw) {
        StringBuilder sb = new StringBuilder();
        for (String line : raw.replace(' ', ' ').split("\n")) {
            String l = line.replaceAll("[ \\t\\u2009\\u202f]+", " ").trim();
            if (l.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append('\n');
            sb.append(l);
        }
        return sb.toString();
    }
}
