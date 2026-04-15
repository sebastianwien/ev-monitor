package com.evmonitor.infrastructure.web;

import com.evmonitor.application.PublicModelService;
import com.evmonitor.domain.CarBrand;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@RestController
@RequiredArgsConstructor
public class SitemapController {

    private static final String BASE_URL = "https://ev-monitor.net";

    private final PublicModelService publicModelService;

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> getSitemap() {
        String today = LocalDate.now().toString();
        Set<String> qualifiedModels = publicModelService.getModelEnumNamesForSitemap(false);

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\"\n");
        sb.append("        xmlns:xhtml=\"http://www.w3.org/1999/xhtml\">\n");

        // Landing page (DE + EN only - no market-specific landing pages for GB/US/NO/SE)
        appendBilingualUrl(sb, BASE_URL + "/", BASE_URL + "/en", "1.0", "weekly", today);

        // Models list - all 6 markets
        appendAllMarketsUrls(sb,
                BASE_URL + "/modelle",
                BASE_URL + "/en/models",
                BASE_URL + "/gb/models",
                BASE_URL + "/us/models",
                BASE_URL + "/no/modeller",
                BASE_URL + "/se/modeller",
                "0.9", "weekly", today);

        // Register (DE only)
        appendSimpleUrl(sb, BASE_URL + "/register", "0.8", "monthly", today);

        for (CarBrand brand : CarBrand.values()) {
            if (brand == CarBrand.SONSTIGE) continue;

            List<CarBrand.CarModel> modelsForBrand = CarBrand.CarModel.byBrand(brand).stream()
                    .filter(m -> qualifiedModels.contains(m.name()))
                    .toList();

            if (modelsForBrand.isEmpty()) continue;

            String brandEncoded = encode(brand.getDisplayString());
            appendAllMarketsUrls(sb,
                    BASE_URL + "/modelle/" + brandEncoded,
                    BASE_URL + "/en/models/" + brandEncoded,
                    BASE_URL + "/gb/models/" + brandEncoded,
                    BASE_URL + "/us/models/" + brandEncoded,
                    BASE_URL + "/no/modeller/" + brandEncoded,
                    BASE_URL + "/se/modeller/" + brandEncoded,
                    "0.8", "weekly", today);

            for (CarBrand.CarModel model : modelsForBrand) {
                String modelSlug = encode(model.getDisplayName().replace(" ", "_"));
                appendAllMarketsUrls(sb,
                        BASE_URL + "/modelle/" + brandEncoded + "/" + modelSlug,
                        BASE_URL + "/en/models/" + brandEncoded + "/" + modelSlug,
                        BASE_URL + "/gb/models/" + brandEncoded + "/" + modelSlug,
                        BASE_URL + "/us/models/" + brandEncoded + "/" + modelSlug,
                        BASE_URL + "/no/modeller/" + brandEncoded + "/" + modelSlug,
                        BASE_URL + "/se/modeller/" + brandEncoded + "/" + modelSlug,
                        "0.7", "weekly", today);
            }
        }

        sb.append("</urlset>");
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic())
                .contentType(MediaType.APPLICATION_XML)
                .body(sb.toString());
    }

    /** Generates 6 <url> entries (one per market), each with full hreflang cross-references. */
    private void appendAllMarketsUrls(StringBuilder sb,
                                       String deLoc, String enLoc, String gbLoc,
                                       String usLoc, String noLoc, String seLoc,
                                       String priority, String changefreq, String lastmod) {
        for (String loc : List.of(deLoc, enLoc, gbLoc, usLoc, noLoc, seLoc)) {
            appendUrl(sb, loc, deLoc, enLoc, gbLoc, usLoc, noLoc, seLoc, priority, changefreq, lastmod);
        }
    }

    /** Generates 2 <url> entries (DE + EN) with bilingual hreflang - used for landing page only. */
    private void appendBilingualUrl(StringBuilder sb, String deLoc, String enLoc,
                                     String priority, String changefreq, String lastmod) {
        appendUrl(sb, deLoc, deLoc, enLoc, null, null, null, null, priority, changefreq, lastmod);
        appendUrl(sb, enLoc, deLoc, enLoc, null, null, null, null, priority, changefreq, lastmod);
    }

    /** Generates a single <url> entry without hreflang (e.g. /register). */
    private void appendSimpleUrl(StringBuilder sb, String loc, String priority, String changefreq, String lastmod) {
        appendUrl(sb, loc, null, null, null, null, null, null, priority, changefreq, lastmod);
    }

    private void appendUrl(StringBuilder sb, String loc,
                           String deLoc, String enLoc, String gbLoc, String usLoc, String noLoc, String seLoc,
                           String priority, String changefreq, String lastmod) {
        sb.append("  <url>\n");
        sb.append("    <loc>").append(loc).append("</loc>\n");
        sb.append("    <lastmod>").append(lastmod).append("</lastmod>\n");
        sb.append("    <changefreq>").append(changefreq).append("</changefreq>\n");
        sb.append("    <priority>").append(priority).append("</priority>\n");
        if (deLoc != null) {
            sb.append("    <xhtml:link rel=\"alternate\" hreflang=\"de\" href=\"").append(deLoc).append("\"/>\n");
            sb.append("    <xhtml:link rel=\"alternate\" hreflang=\"en\" href=\"").append(enLoc).append("\"/>\n");
            if (gbLoc != null) {
                sb.append("    <xhtml:link rel=\"alternate\" hreflang=\"en-GB\" href=\"").append(gbLoc).append("\"/>\n");
                sb.append("    <xhtml:link rel=\"alternate\" hreflang=\"en-US\" href=\"").append(usLoc).append("\"/>\n");
                sb.append("    <xhtml:link rel=\"alternate\" hreflang=\"nb\" href=\"").append(noLoc).append("\"/>\n");
                sb.append("    <xhtml:link rel=\"alternate\" hreflang=\"sv\" href=\"").append(seLoc).append("\"/>\n");
            }
            sb.append("    <xhtml:link rel=\"alternate\" hreflang=\"x-default\" href=\"").append(enLoc).append("\"/>\n");
        }
        sb.append("  </url>\n");
    }

    private String encode(String segment) {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
