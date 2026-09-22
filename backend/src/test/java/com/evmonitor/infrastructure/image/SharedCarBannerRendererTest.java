package com.evmonitor.infrastructure.image;

import com.evmonitor.application.PublicCarResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Forum-Signatur-Banner (468x60) der geteilten Fahrzeugseite. Reine Funktion
 * von Kennzahlen auf PNG-Bytes, kein Spring-Kontext.
 */
class SharedCarBannerRendererTest {

    private final SharedCarBannerRenderer renderer = new SharedCarBannerRenderer();

    @BeforeAll
    static void headless() {
        System.setProperty("java.awt.headless", "true");
    }

    @Test
    void rendersFullBannerDimensions() throws Exception {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(renderer.render(car("Tesla Model 3", peers()))));
        assertNotNull(img, "Ausgabe muss ein lesbares PNG sein");
        assertEquals(468, img.getWidth());
        assertEquals(60, img.getHeight());
    }

    @Test
    void rendersWithoutPeers() throws Exception {
        assertNotNull(ImageIO.read(new ByteArrayInputStream(renderer.render(car("Tesla Model 3", null)))));
    }

    @Test
    void rendersWithoutModelAndWithoutConsumption() throws Exception {
        PublicCarResponse empty = new PublicCarResponse(null, null, false, null, null, null, null, null, null,
                null, null, null, null, null, List.of(), List.of());
        assertNotNull(ImageIO.read(new ByteArrayInputStream(renderer.render(empty))));
    }

    @Test
    void longModelNameStaysInsideTheBanner() throws Exception {
        String longName = "Mercedes-Benz EQS 580 4MATIC SUV Electric Art Advanced Plus Edition";
        byte[] png = renderer.render(car(longName, peers()));
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
        assertEquals(468, img.getWidth());
        // Der Verbrauch muss trotz langem Namen noch ins Bild: das Ergebnis darf
        // nicht mit dem eines Autos ohne Verbrauch identisch sein.
        PublicCarResponse noConsumption = new PublicCarResponse(longName, 2024, false, null, 42, null, null, null,
                null, null, null, null, null, peers(), List.of(), List.of());
        assertFalse(java.util.Arrays.equals(png, renderer.render(noConsumption)));
    }

    @Test
    void languageChangesTheTexts() {
        PublicCarResponse car = car("Tesla Model 3", peers());
        byte[] de = renderer.render(car, PublicImageStyle.Lang.DE);
        byte[] en = renderer.render(car, PublicImageStyle.Lang.EN);
        assertFalse(java.util.Arrays.equals(de, en), "englische Einordnung muss anders aussehen");
        assertArrayEquals(de, renderer.render(car, PublicImageStyle.Lang.of("xx")), "unbekannte Sprache faellt auf Deutsch zurueck");
    }

    @Test
    void peerDeltaIsRoundedPercent() {
        assertEquals(-8, SharedCarBannerRenderer.peerDeltaPercent(car("X", peers())));
        assertNull(SharedCarBannerRenderer.peerDeltaPercent(car("X", null)));
    }

    @Test
    void nullInputRendersNothing() {
        assertNull(renderer.render(null));
    }

    private static PublicCarResponse.PeerComparison peers() {
        return new PublicCarResponse.PeerComparison(new BigDecimal("16.5"), 12, "MODEL");
    }

    private static PublicCarResponse car(String model, PublicCarResponse.PeerComparison peers) {
        return new PublicCarResponse(model, 2024, false, "/modelle/Tesla/Model_3", 42,
                new BigDecimal("1234.5"), new BigDecimal("8100"), new BigDecimal("15.2"),
                new BigDecimal("0.35"), new BigDecimal("5.32"), new BigDecimal("40"),
                new BigDecimal("14.1"), new BigDecimal("17.9"), peers, List.of(), List.of());
    }
}
