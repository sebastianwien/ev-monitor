package com.evmonitor.infrastructure.image;

import com.evmonitor.application.PowerCurveResponse;
import com.evmonitor.application.PublicCurveResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test fuer das Vorschaubild geteilter Ladekurven. Kein Spring-Kontext -
 * der Renderer ist eine reine Funktion von Kurvendaten auf PNG-Bytes.
 */
class PowerCurveImageRendererTest {

    private final PowerCurveImageRenderer renderer = new PowerCurveImageRenderer();

    @BeforeAll
    static void headless() {
        // Ohne Headless-Modus versucht AWT auf einem Build-Server ein Display zu oeffnen.
        System.setProperty("java.awt.headless", "true");
    }

    @Test
    void rendersPngWithSocialCardDimensions() throws Exception {
        byte[] png = renderer.render(curve(points(30), "Tesla Model 3", "Tesla Supercharger"));

        BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
        assertNotNull(img, "Ausgabe muss ein lesbares PNG sein");
        assertEquals(PowerCurveImageRenderer.WIDTH, img.getWidth());
        assertEquals(PowerCurveImageRenderer.HEIGHT, img.getHeight());
        // 1200x630 ist das Format, das Facebook, LinkedIn und X erwarten.
        assertEquals(1200, img.getWidth());
        assertEquals(630, img.getHeight());
    }

    @Test
    void plotWindowKeepsTheAspectRatioOfTheAppChart() {
        // Das Chart in der App hat viewBox 600x200 = 3:1. Weicht das Bild davon
        // ab, sieht die geteilte Kurve flacher aus als die, die der Nutzer kennt.
        double ratio = (double) (PowerCurveImageRenderer.PLOT_RIGHT - PowerCurveImageRenderer.PLOT_LEFT)
                / (PowerCurveImageRenderer.PLOT_BOTTOM - PowerCurveImageRenderer.PLOT_TOP);
        assertTrue(ratio >= 2.8 && ratio <= 3.4, "Plotfenster-Verhaeltnis war " + ratio);
    }

    @Test
    void socAxisChangesTheImage() {
        // Belegt, dass der Ladestand tatsaechlich gezeichnet wird: dieselbe Kurve
        // einmal mit und einmal ohne ableitbaren SoC ergibt verschiedene Bilder.
        PublicCurveResponse withSoc = curve(points(30), "Tesla Model 3", "Tesla Supercharger");
        PublicCurveResponse withoutSoc = new PublicCurveResponse(
                pointsWithoutSoc(30), "Tesla Model 3", new BigDecimal("43.8"), 17,
                null, null, new BigDecimal("253.5"), "Tesla Supercharger", "DC",
                LocalDate.of(2026, 8, 2));

        assertFalse(java.util.Arrays.equals(renderer.render(withSoc), renderer.render(withoutSoc)));
    }

    @Test
    void rendersWithoutCpoName() throws Exception {
        byte[] png = renderer.render(curve(points(30), "VW ID.4", null));
        assertNotNull(ImageIO.read(new ByteArrayInputStream(png)));
    }

    @Test
    void rendersWithoutCarModel() throws Exception {
        // Ein geloeschtes oder unbekanntes Modell darf das Bild nicht sprengen.
        byte[] png = renderer.render(curve(points(30), null, "Ionity"));
        assertNotNull(ImageIO.read(new ByteArrayInputStream(png)));
    }

    @Test
    void rendersSinglePointCurve() throws Exception {
        byte[] png = renderer.render(curve(points(1), "Tesla Model 3", null));
        assertNotNull(ImageIO.read(new ByteArrayInputStream(png)));
    }

    @Test
    void rendersWhenAllMetricsAreMissing() throws Exception {
        PublicCurveResponse curve = new PublicCurveResponse(
                points(10), null, null, null, null, null, null, null, null, null);
        byte[] png = renderer.render(curve);
        assertNotNull(ImageIO.read(new ByteArrayInputStream(png)));
    }

    @Test
    void returnsNullForEmptyCurve() {
        PublicCurveResponse curve = new PublicCurveResponse(
                List.of(), "Tesla Model 3", null, null, null, null, null, null, null, null);
        assertNull(renderer.render(curve), "Ohne Punkte gibt es kein Bild zu rendern");
    }

    @Test
    void twoRendersOfSameCurveAreIdentical() {
        // Deterministisch, damit das Bild cachebar ist und Crawler bei jedem
        // Abruf dasselbe sehen.
        PublicCurveResponse curve = curve(points(30), "Tesla Model 3", "Tesla Supercharger");
        assertArrayEquals(renderer.render(curve), renderer.render(curve));
    }

    private static List<PowerCurveResponse.Point> points(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> new PowerCurveResponse.Point(
                        1_700_000_000_000L + i * 30_000L,
                        250.0 - i * 5.0,
                        8.0 + i * 2.0))
                .toList();
    }

    /** Bestandskurven aus der Zeit vor der SoC-Anreicherung tragen keinen Ladestand. */
    private static List<PowerCurveResponse.Point> pointsWithoutSoc(int count) {
        return points(count).stream()
                .map(p -> new PowerCurveResponse.Point(p.ts(), p.kw(), null))
                .toList();
    }

    private static PublicCurveResponse curve(List<PowerCurveResponse.Point> points, String model, String cpo) {
        return new PublicCurveResponse(
                points,
                model,
                new BigDecimal("43.8"),
                17,
                new BigDecimal("8"),
                new BigDecimal("66.45"),
                new BigDecimal("253.5"),
                cpo,
                "DC",
                LocalDate.of(2026, 8, 2));
    }
}
