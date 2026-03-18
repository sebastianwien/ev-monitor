package com.evmonitor.infrastructure.web;

import com.evmonitor.domain.CarBrand;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@RestController
public class SitemapController {

    private static final String BASE_URL = "https://ev-monitor.net";

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public String getSitemap() {
        String today = LocalDate.now().toString();
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        appendUrl(sb, BASE_URL + "/", "1.0", "weekly", today);
        appendUrl(sb, BASE_URL + "/modelle", "0.9", "weekly", today);
        appendUrl(sb, BASE_URL + "/register", "0.8", "monthly", today);

        for (CarBrand brand : CarBrand.values()) {
            if (brand == CarBrand.SONSTIGE) continue;

            String brandEncoded = encode(brand.getDisplayString());
            appendUrl(sb, BASE_URL + "/modelle/" + brandEncoded, "0.8", "weekly", today);

            List<CarBrand.CarModel> models = CarBrand.CarModel.byBrand(brand);
            for (CarBrand.CarModel model : models) {
                String modelSlug = encode(model.getDisplayName().replace(" ", "_"));
                appendUrl(sb, BASE_URL + "/modelle/" + brandEncoded + "/" + modelSlug, "0.7", "weekly", today);
            }
        }

        sb.append("</urlset>");
        return sb.toString();
    }

    private void appendUrl(StringBuilder sb, String loc, String priority, String changefreq, String lastmod) {
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
