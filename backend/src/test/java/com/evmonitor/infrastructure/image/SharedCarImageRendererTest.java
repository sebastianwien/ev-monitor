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

/** Vorschaubild der geteilten Fahrzeugseite: Masse, Sprache, Robustheit ohne Daten. */
class SharedCarImageRendererTest {

    private final SharedCarImageRenderer renderer = new SharedCarImageRenderer();

    @BeforeAll
    static void headless() {
        System.setProperty("java.awt.headless", "true");
    }

    @Test
    void rendersOgDimensionsInEveryLanguage() throws Exception {
        for (PublicImageStyle.Lang lang : PublicImageStyle.Lang.values()) {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(renderer.render(car(), lang)));
            assertEquals(1200, img.getWidth(), lang.name());
            assertEquals(630, img.getHeight(), lang.name());
        }
    }

    @Test
    void rendersWithoutAnyNumbers() {
        PublicCarResponse empty = new PublicCarResponse(null, null, false, null, null, null, null, null,
                null, null, null, null, null, null, List.of(), List.of());
        assertNotNull(renderer.render(empty));
    }

    @Test
    void usesPaperBackground() throws Exception {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(renderer.render(car())));
        assertEquals(PublicImageStyle.PAPER.getRGB(), img.getRGB(5, 5));
    }

    private static PublicCarResponse car() {
        return new PublicCarResponse("Tesla Model 3", 2024, false, "/modelle/tesla/model_3", 42,
                new BigDecimal("2100"), new BigDecimal("12000"), new BigDecimal("15.2"), new BigDecimal("0.31"),
                new BigDecimal("4.71"), new BigDecimal("22"), new BigDecimal("14.1"), new BigDecimal("17.9"),
                new PublicCarResponse.PeerComparison(new BigDecimal("16.5"), 12, "MODEL"), List.of(), List.of());
    }
}
