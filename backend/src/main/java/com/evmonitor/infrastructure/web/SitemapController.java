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

        // Landing page
        appendBilingualUrl(sb, BASE_URL + "/", BASE_URL + "/en", "1.0", "weekly", today);

        // Models list
        appendBilingualUrl(sb, BASE_URL + "/modelle", BASE_URL + "/en/models", "0.9", "weekly", today);

        // Register (DE only - no EN auth pages in Phase 1)
        appendUrl(sb, BASE_URL + "/register", null, null, "0.8", "monthly", today);

        for (CarBrand brand : CarBrand.values()) {
            if (brand == CarBrand.SONSTIGE) continue;

            List<CarBrand.CarModel> modelsForBrand = CarBrand.CarModel.byBrand(brand).stream()
                    .filter(m -> qualifiedModels.contains(m.name()))
                    .toList();

            if (modelsForBrand.isEmpty()) continue;

            String brandEncoded = encode(brand.getDisplayString());
            String deBrandUrl = BASE_URL + "/modelle/" + brandEncoded;
            String enBrandUrl = BASE_URL + "/en/models/" + brandEncoded;
            appendBilingualUrl(sb, deBrandUrl, enBrandUrl, "0.8", "weekly", today);

            for (CarBrand.CarModel model : modelsForBrand) {
                String modelSlug = encode(model.getDisplayName().replace(" ", "_"));
                String deModelUrl = BASE_URL + "/modelle/" + brandEncoded + "/" + modelSlug;
                String enModelUrl = BASE_URL + "/en/models/" + brandEncoded + "/" + modelSlug;
                appendBilingualUrl(sb, deModelUrl, enModelUrl, "0.7", "weekly", today);
            }
        }

        sb.append("</urlset>");
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic())
                .contentType(MediaType.APPLICATION_XML)
                .body(sb.toString());
    }

    private void appendBilingualUrl(StringBuilder sb, String deLoc, String enLoc,
                                    String priority, String changefreq, String lastmod) {
        // DE entry
        appendUrl(sb, deLoc, deLoc, enLoc, priority, changefreq, lastmod);
        // EN entry
        appendUrl(sb, enLoc, deLoc, enLoc, priority, changefreq, lastmod);
    }

    private void appendUrl(StringBuilder sb, String loc, String deHref, String enHref,
                           String priority, String changefreq, String lastmod) {
        sb.append("  <url>\n");
        sb.append("    <loc>").append(loc).append("</loc>\n");
        sb.append("    <lastmod>").append(lastmod).append("</lastmod>\n");
        sb.append("    <changefreq>").append(changefreq).append("</changefreq>\n");
        sb.append("    <priority>").append(priority).append("</priority>\n");
        if (deHref != null && enHref != null) {
            sb.append("    <xhtml:link rel=\"alternate\" hreflang=\"de\" href=\"").append(deHref).append("\"/>\n");
            sb.append("    <xhtml:link rel=\"alternate\" hreflang=\"en\" href=\"").append(enHref).append("\"/>\n");
            sb.append("    <xhtml:link rel=\"alternate\" hreflang=\"x-default\" href=\"").append(enHref).append("\"/>\n");
        }
        sb.append("  </url>\n");
    }

    private String encode(String segment) {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
