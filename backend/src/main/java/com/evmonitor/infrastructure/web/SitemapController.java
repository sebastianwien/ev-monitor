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

    // No `produces` restriction: a sitemap is a fixed XML resource. Crawlers/feed readers send
    // varying Accept headers (text/xml, application/rss+xml, ...) and content negotiation would
    // reject those with 406. The content type is set explicitly on the response body below.
    @GetMapping(value = "/sitemap.xml")
    public ResponseEntity<String> getSitemap() {
        String today = LocalDate.now().toString();
        Set<String> qualifiedModels = publicModelService.getModelEnumNamesForSitemap(false);

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        // Nur DE: fremdsprachige Varianten (/en, /gb, /no, /se) bleiben erreichbar, tragen aber
        // noindex und stehen nicht in der Sitemap, damit sie die DE-Seiten nicht kannibalisieren.
        appendSimpleUrl(sb, BASE_URL + "/", "1.0", "weekly", today);
        appendSimpleUrl(sb, BASE_URL + "/modelle", "0.9", "weekly", today);

        // Register (DE only)
        appendSimpleUrl(sb, BASE_URL + "/register", "0.8", "monthly", today);

        for (CarBrand brand : CarBrand.values()) {
            if (brand == CarBrand.SONSTIGE) continue;

            List<CarBrand.CarModel> modelsForBrand = CarBrand.CarModel.byBrand(brand).stream()
                    .filter(m -> qualifiedModels.contains(m.name()))
                    .toList();

            if (modelsForBrand.isEmpty()) continue;

            String brandEncoded = encode(brand.getDisplayString());
            appendSimpleUrl(sb, BASE_URL + "/modelle/" + brandEncoded, "0.8", "weekly", today);

            for (CarBrand.CarModel model : modelsForBrand) {
                String modelSlug = encode(model.getDisplayName().replace(" ", "_"));
                appendSimpleUrl(sb, BASE_URL + "/modelle/" + brandEncoded + "/" + modelSlug, "0.7", "weekly", today);
            }
        }

        sb.append("</urlset>");
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic())
                .contentType(MediaType.APPLICATION_XML)
                .body(sb.toString());
    }

    private void appendSimpleUrl(StringBuilder sb, String loc, String priority, String changefreq, String lastmod) {
        sb.append("  <url>\n");
        sb.append("    <loc>").append(loc).append("</loc>\n");
        sb.append("    <lastmod>").append(lastmod).append("</lastmod>\n");
        sb.append("    <changefreq>").append(changefreq).append("</changefreq>\n");
        sb.append("    <priority>").append(priority).append("</priority>\n");
        sb.append("  </url>\n");
    }

    private String encode(String segment) {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
