package com.evmonitor.application.seo;

import com.evmonitor.application.seo.SeoAudit.Check;
import com.evmonitor.application.seo.SeoAudit.Finding;
import com.evmonitor.application.seo.SeoAudit.PageReport;
import com.evmonitor.application.seo.SeoAudit.Severity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Erfundenes HTML auf einer erfundenen Domain; geprüft wird nur, was Googlebot ohne JS sieht. */
class PageAuditorTest {

    private static final String MODEL = "/modelle/Testmarke/Blitz_5";
    private final SeoAuditProperties properties = properties();
    private final PageAuditor auditor = new PageAuditor(properties, new ObjectMapper());

    private static SeoAuditProperties properties() {
        SeoAuditProperties p = new SeoAuditProperties();
        p.setSiteHost("example.test");
        return p;
    }

    private static String page(String path, String head, String body) {
        return "<html><head>" + head + "</head><body>" + body + "</body></html>";
    }

    private static String goodHead(String path, String jsonLdTypes) {
        StringBuilder ld = new StringBuilder();
        for (String type : jsonLdTypes.split(",")) {
            if (type.isBlank()) continue;
            // Product nur mit Bewertungsschnitt, so wie die Modellseite es ausliefert
            String extra = type.equals("Product") ? ",\"aggregateRating\":{\"@type\":\"AggregateRating\",\"ratingValue\":4.1,\"ratingCount\":7}" : "";
            ld.append("<script type=\"application/ld+json\">{\"@context\":\"https://schema.org\",\"@type\":\"")
                    .append(type).append("\"").append(extra).append("}</script>");
        }
        return "<title>Testmarke Blitz 5: echter Verbrauch und Reichweite</title>"
                + "<meta name=\"description\" content=\"Testmarke Blitz 5 im Alltag: echter Verbrauch aus vielen Ladevorgängen, Reichweite im Winter und Ladekosten.\">"
                + "<meta name=\"robots\" content=\"index, follow\">"
                + "<link rel=\"canonical\" href=\"https://example.test" + path + "\">" + ld;
    }

    private static final String GOOD_MODEL_BODY = "<h1>Testmarke Blitz 5 - Verbrauch</h1><p>Ø 17,3 kWh/100km real</p>"
            + "<a href=\"/modelle/Testmarke\">Testmarke</a><a href=\"https://example.test/modelle\">Alle</a>"
            + "<a href=\"https://fremd.test/x\">fremd</a><a href=\"#faq\">FAQ</a><a href=\"/img/a.png\">Bild</a>";

    private PageReport goodModel() {
        return auditor.audit(MODEL, 200, page(MODEL, goodHead(MODEL, "BreadcrumbList,Product"), GOOD_MODEL_BODY));
    }

    private static List<Check> checks(PageReport r) {
        return r.findings().stream().map(Finding::check).toList();
    }

    @Test
    void cleanModelPageHasNoFindingsAndOnlyInternalPageLinks() {
        PageReport r = goodModel();

        assertThat(r.findings()).isEmpty();
        assertThat(r.noindex()).isFalse();
        assertThat(r.links()).containsExactlyInAnyOrder("/modelle/Testmarke", "/modelle");
    }

    @Test
    void non200StopsFurtherChecks() {
        PageReport r = auditor.audit(MODEL, 404, "<html><head></head><body></body></html>");

        assertThat(r.findings()).extracting(Finding::check, Finding::severity).containsExactly(org.assertj.core.groups.Tuple.tuple(Check.STATUS, Severity.ERROR));
        assertThat(r.links()).isEmpty();
    }

    @Test
    void unreachableIsStatusError() {
        PageReport r = auditor.audit(MODEL, 0, null);

        assertThat(checks(r)).containsExactly(Check.STATUS);
        assertThat(r.findings().get(0).message()).contains("nicht erreichbar");
    }

    @Test
    void noindexOnSitemapPageIsError() {
        String head = goodHead(MODEL, "BreadcrumbList,Product").replace("index, follow", "noindex, nofollow");
        PageReport r = auditor.audit(MODEL, 200, page(MODEL, head, GOOD_MODEL_BODY));

        assertThat(r.noindex()).isTrue();
        assertThat(checks(r)).containsExactly(Check.NOINDEX);
    }

    @Test
    void canonicalMissingOrForeignIsError() {
        String missing = goodHead(MODEL, "BreadcrumbList,Product").replaceAll("<link rel=\"canonical\"[^>]*>", "");
        String other = goodHead(MODEL, "BreadcrumbList,Product").replace("https://example.test" + MODEL, "https://example.test/modelle/testmarke/blitz_5");

        assertThat(checks(auditor.audit(MODEL, 200, page(MODEL, missing, GOOD_MODEL_BODY)))).containsExactly(Check.CANONICAL);
        PageReport r = auditor.audit(MODEL, 200, page(MODEL, other, GOOD_MODEL_BODY));
        assertThat(checks(r)).containsExactly(Check.CANONICAL);
        assertThat(r.findings().get(0).message()).contains("/modelle/testmarke/blitz_5");
    }

    @Test
    void titleAndDescriptionLengthAreWarnings() {
        String head = goodHead(MODEL, "BreadcrumbList,Product")
                .replace("Testmarke Blitz 5: echter Verbrauch und Reichweite</title>", "Kurz</title>")
                .replaceAll("content=\"Testmarke Blitz 5 im Alltag[^\"]*\"", "content=\"zu kurz\"");
        PageReport r = auditor.audit(MODEL, 200, page(MODEL, head, GOOD_MODEL_BODY));

        assertThat(r.findings()).extracting(Finding::check).containsExactlyInAnyOrder(Check.TITLE, Check.DESCRIPTION);
        assertThat(r.findings()).allMatch(f -> f.severity() == Severity.WARNING);
        assertThat(r.findings()).extracting(Finding::message).anyMatch(m -> m.contains("4 Zeichen"));
    }

    @Test
    void missingDescriptionIsError() {
        String head = goodHead(MODEL, "BreadcrumbList,Product").replaceAll("<meta name=\"description\"[^>]*>", "");
        PageReport r = auditor.audit(MODEL, 200, page(MODEL, head, GOOD_MODEL_BODY));

        assertThat(r.findings()).extracting(Finding::check, Finding::severity)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(Check.DESCRIPTION, Severity.ERROR));
    }

    @Test
    void zeroOrTwoH1AreWarnings() {
        String two = GOOD_MODEL_BODY + "<h1>Noch eine</h1>";
        PageReport r = auditor.audit(MODEL, 200, page(MODEL, goodHead(MODEL, "BreadcrumbList,Product"), two));

        assertThat(checks(r)).containsExactly(Check.H1);
        assertThat(r.findings().get(0).message()).contains("2");
    }

    @Test
    void jsonLdMustParseAndCarryRequiredTypesPerPageType() {
        String broken = goodHead(MODEL, "BreadcrumbList,Product") + "<script type=\"application/ld+json\">{kaputt</script>";
        PageReport parse = auditor.audit(MODEL, 200, page(MODEL, broken, GOOD_MODEL_BODY));
        assertThat(checks(parse)).containsExactly(Check.JSONLD);

        // Product setzt die Modellseite nur mit Fahrerbewertungen; ohne Product ist alles in Ordnung
        PageReport noProduct = auditor.audit(MODEL, 200, page(MODEL, goodHead(MODEL, "BreadcrumbList,WebPage"), GOOD_MODEL_BODY));
        assertThat(noProduct.findings()).isEmpty();

        // fehlende Pflicht-Typen sind Hinweise, keine Fehler
        PageReport noBreadcrumb = auditor.audit(MODEL, 200, page(MODEL, goodHead(MODEL, "WebPage"), GOOD_MODEL_BODY));
        assertThat(noBreadcrumb.findings()).extracting(Finding::check, Finding::severity)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(Check.JSONLD, Severity.WARNING));
        assertThat(noBreadcrumb.findings().get(0).message()).contains("BreadcrumbList");

        String brand = "/modelle/Testmarke";
        String brandBody = "<h1>Testmarke</h1>";
        assertThat(auditor.audit(brand, 200, page(brand, goodHead(brand, "BreadcrumbList,ItemList"), brandBody)).findings()).isEmpty();
        assertThat(auditor.audit(brand, 200, page(brand, goodHead(brand, "BreadcrumbList"), brandBody)).findings())
                .extracting(Finding::check, Finding::severity).containsExactly(org.assertj.core.groups.Tuple.tuple(Check.JSONLD, Severity.WARNING));

        String list = "/modelle";
        assertThat(auditor.audit(list, 200, page(list, goodHead(list, "ItemList"), "<h1>Alle</h1>")).findings()).isEmpty();
        String home = "/";
        assertThat(auditor.audit(home, 200, page(home, goodHead(home, ""), "<h1>Start</h1>")).findings()).isEmpty();
    }

    @Test
    void jsonLdTypesInArrayAndGraphCount() {
        String head = goodHead(MODEL, "")
                + "<script type=\"application/ld+json\">{\"@graph\":[{\"@type\":\"BreadcrumbList\"},{\"@type\":[\"Product\",\"Car\"],\"review\":[{\"@type\":\"Review\"}]}]}</script>";
        assertThat(auditor.audit(MODEL, 200, page(MODEL, head, GOOD_MODEL_BODY)).findings()).isEmpty();
    }

    /** Rückfallschutz Meilen-Bug: DE-Modellseite in mi/kWh und Dollar muss auffallen. */
    @Test
    void prerenderGuardCatchesImperialUnitsAndDollar() {
        String body = "<h1>Testmarke Blitz 5 - Verbrauch</h1><p>3,4 mi/kWh real, 254 miles, 0,31 $/kWh</p>";
        PageReport r = auditor.audit(MODEL, 200, page(MODEL, goodHead(MODEL, "BreadcrumbList,Product"), body));

        assertThat(r.findings()).extracting(Finding::check).contains(Check.PRERENDER);
        assertThat(r.findings()).filteredOn(f -> f.check() == Check.PRERENDER).extracting(Finding::message)
                .anyMatch(m -> m.contains("kWh/100 km"))
                .anyMatch(m -> m.contains("mi/kWh"));
    }

    @Test
    void prerenderGuardNeedsModelNameInH1() {
        String body = "<h1>Elektroauto</h1><p>Ø 17,3 kWh/100 km real</p>";
        PageReport r = auditor.audit(MODEL, 200, page(MODEL, goodHead(MODEL, "BreadcrumbList,Product"), body));

        assertThat(checks(r)).containsExactly(Check.PRERENDER);
        assertThat(r.findings().get(0).message()).contains("Blitz 5");
    }

    @Test
    void dollarInsideScriptIsNotVisibleTextAndIgnored() {
        String body = GOOD_MODEL_BODY + "<script>var price = '$' + 5;</script>";
        assertThat(auditor.audit(MODEL, 200, page(MODEL, goodHead(MODEL, "BreadcrumbList,Product"), body)).findings()).isEmpty();
    }

    @Test
    void scoreDeductsPerSeverity() {
        List<Finding> f = List.of(new Finding(Check.STATUS, Severity.ERROR, "a"), new Finding(Check.H1, Severity.WARNING, "b"));
        assertThat(SeoAudit.score(f)).isEqualTo(75);
        assertThat(SeoAudit.score(List.of(f.get(0), f.get(0), f.get(0), f.get(0), f.get(0), f.get(0)))).isZero();
    }

    /** Google verwirft ein Product ohne review, aggregateRating oder offers; das ist ein echter Fehler. */
    @Test
    void productWithoutReviewRatingOrOfferIsError() {
        String bare = goodHead(MODEL, "BreadcrumbList")
                + "<script type=\"application/ld+json\">{\"@type\":\"Product\",\"name\":\"Blitz 5\"}</script>";
        PageReport r = auditor.audit(MODEL, 200, page(MODEL, bare, GOOD_MODEL_BODY));
        assertThat(r.findings()).extracting(Finding::check, Finding::severity)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(Check.JSONLD, Severity.ERROR));
        assertThat(r.findings().get(0).message()).contains("Bewertung");

        String withReview = goodHead(MODEL, "BreadcrumbList")
                + "<script type=\"application/ld+json\">{\"@type\":\"Product\",\"review\":{\"@type\":\"Review\"}}</script>";
        assertThat(auditor.audit(MODEL, 200, page(MODEL, withReview, GOOD_MODEL_BODY)).findings()).isEmpty();
    }
}
