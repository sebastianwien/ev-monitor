package com.evmonitor.infrastructure.image;

import com.evmonitor.infrastructure.image.PublicImageStyle.Lang;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Die Bildtexte (Vorschaubild, Banner) muessen wortgleich mit dem Block
 * {@code share_car} der Frontend-Locales sein - die Bilder haengen an der Seite
 * und sollen nichts anderes behaupten als sie. Java kann die YAML-Dateien zur
 * Laufzeit nicht laden, deshalb sind die Texte kopiert und hier gegen die
 * Quelle geprueft. Ohne Frontend-Checkout (z. B. Docker-Build) wird uebersprungen.
 */
class PublicImageTextsMatchLocalesTest {

    private static final Path LOCALES = Path.of("..", "frontend", "src", "locales");

    @ParameterizedTest
    @EnumSource(Lang.class)
    void imageTextsEqualShareCarLocale(Lang lang) throws IOException {
        Path file = LOCALES.resolve(lang.code + ".yaml");
        Assumptions.assumeTrue(Files.exists(file), "Frontend-Locales nicht im Checkout: " + file);
        Map<String, String> t = shareCar(file);

        assertEquals(t.get("masthead_claim"), lang.claim());
        assertEquals(t.get("fallback_title"), lang.fallbackModel());
        assertEquals(t.get("charges_count").replace("{n}", "42"), lang.charges(42));
        assertEquals(t.get("tile_cost_per_100"), lang.labelCostPer100());
        assertEquals(t.get("tile_distance"), lang.labelDistance());
        assertEquals(t.get("tile_public_share"), lang.labelPublicShare());
        assertEquals(t.get("tile_kwh"), lang.labelCharged());

        String model = t.get("peer_scope_model");
        String spec = t.get("peer_scope_spec");
        assertEquals(fill(t.get("peer_below"), "8", model), lang.peerHeadline(-8, false));
        assertEquals(fill(t.get("peer_above"), "12", spec), lang.peerHeadline(12, true));
        assertEquals(fill(t.get("peer_equal"), null, model), lang.peerHeadline(0, false));

        String[] detail = t.get("peer_detail").split(" \\| ");
        assertEquals("Ø 16,5 kWh/100km · " + detail[0].replace("{n}", "1"), lang.peerDetail("16,5", 1));
        assertEquals("Ø 16,5 kWh/100km · " + detail[1].replace("{n}", "12"), lang.peerDetail("16,5", 12));
    }

    private static String fill(String template, String pct, String scope) {
        String s = template.replace("{scope}", scope);
        return pct == null ? s : s.replace("{pct}", pct);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> shareCar(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            Map<String, Object> root = new Yaml().load(in);
            return (Map<String, String>) root.get("share_car");
        }
    }
}
