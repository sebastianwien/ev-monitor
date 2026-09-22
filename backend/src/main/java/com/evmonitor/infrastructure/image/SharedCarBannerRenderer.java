package com.evmonitor.infrastructure.image;

import com.evmonitor.application.PublicCarResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * Forum-Signatur-Banner (468x60) der geteilten Fahrzeugseite. Wird in Foren
 * unter jedem Beitrag geladen und verlinkt auf die Seite - deshalb knapp,
 * gut lesbar und mit denselben Zahlen wie das grosse Vorschaubild.
 *
 * Aufbau von links: Marke, Modell, Verbrauch, Einordnung gegen den
 * Modell-Schnitt. Was nicht mehr in die Breite passt, wird von rechts nach
 * links weggelassen, der Modellname notfalls mit Ellipse gekuerzt. Nichts
 * laeuft ueber den Rand.
 */
@Component
public class SharedCarBannerRenderer {

    private static final Logger log = LoggerFactory.getLogger(SharedCarBannerRenderer.class);

    public static final int WIDTH = 468;
    public static final int HEIGHT = 60;
    private static final int PAD = 12;
    private static final int GAP = 10;
    private static final int BASELINE = 37;

    private static final Color BG = new Color(0x0F172A);
    private static final Color BG_ACCENT = new Color(0x14243F);
    private static final Color EMERALD = new Color(0x10B981);
    private static final Color AMBER = new Color(0xF59E0B);
    private static final Color TEXT = new Color(0xF1F5F9);
    private static final Color TEXT_MUTED = new Color(0x94A3B8);
    private static final Locale NUM = Locale.GERMANY;

    private static final Font BRAND_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 11);
    private static final Font MODEL_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 15);
    private static final Font VALUE_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 17);
    private static final Font UNIT_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
    private static final Font PEER_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 12);

    /** @return PNG-Bytes oder {@code null}, wenn Eingabe fehlt oder das Rendern fehlschlaegt. */
    public byte[] render(PublicCarResponse car) {
        if (car == null) return null;
        try {
            BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setPaint(new GradientPaint(0, 0, BG_ACCENT, WIDTH, HEIGHT, BG));
                g.fillRect(0, 0, WIDTH, HEIGHT);
                g.setColor(EMERALD);
                g.fillRect(0, 0, 4, HEIGHT);
                paintContent(g, car);
            } finally {
                g.dispose();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            log.warn("Signatur-Banner der Fahrzeugseite fehlgeschlagen: {}", e.getMessage());
            return null;
        }
    }

    private void paintContent(Graphics2D g, PublicCarResponse car) {
        int x = PAD + 4;
        int right = WIDTH - PAD;

        // Marke vertikal in zwei Zeilen links, damit sie wenig Breite braucht.
        g.setColor(EMERALD);
        g.setFont(BRAND_FONT);
        g.drawString("EV", x, 27);
        g.drawString("MONITOR", x, 41);
        x += g.getFontMetrics(BRAND_FONT).stringWidth("MONITOR") + GAP;

        // Rechts zuerst reservieren, was fest dazugehoert: Verbrauch und Einordnung.
        // Der Modellname bekommt den Rest und wird gekuerzt.
        String value = car.avgConsumptionKwhPer100km() == null ? null : num(car.avgConsumptionKwhPer100km(), 1);
        String unit = "kWh/100km";
        int valueWidth = value == null ? 0
                : g.getFontMetrics(VALUE_FONT).stringWidth(value) + 4 + g.getFontMetrics(UNIT_FONT).stringWidth(unit);

        Integer delta = peerDeltaPercent(car);
        String peer = delta == null ? null
                : delta == 0 ? "im Schnitt" : Math.abs(delta) + " % " + (delta < 0 ? "unter" : "über") + " Schnitt";
        int peerWidth = peer == null ? 0 : g.getFontMetrics(PEER_FONT).stringWidth(peer);

        int reserved = (value == null ? 0 : valueWidth + GAP) + (peer == null ? 0 : peerWidth + GAP);
        int modelMax = right - x - reserved;
        if (modelMax < 60 && peer != null) {
            // Zu eng: die Einordnung faellt weg, der Verbrauch bleibt.
            peer = null;
            reserved -= peerWidth + GAP;
            modelMax = right - x - reserved;
        }

        // Ohne Jahr: die Breite gehoert dem Modellnamen, das Jahr steht auf der Seite.
        String model = car.carModel() != null ? car.carModel() : "Fahrzeug";
        g.setColor(TEXT);
        g.setFont(MODEL_FONT);
        String shown = fit(model, g.getFontMetrics(MODEL_FONT), modelMax);
        g.drawString(shown, x, BASELINE);
        x += g.getFontMetrics(MODEL_FONT).stringWidth(shown) + GAP;

        if (value != null) {
            g.setColor(TEXT);
            g.setFont(VALUE_FONT);
            g.drawString(value, x, BASELINE);
            x += g.getFontMetrics(VALUE_FONT).stringWidth(value) + 4;
            g.setColor(TEXT_MUTED);
            g.setFont(UNIT_FONT);
            g.drawString(unit, x, BASELINE);
            x += g.getFontMetrics(UNIT_FONT).stringWidth(unit) + GAP;
        }

        if (peer != null) {
            g.setColor(delta <= 0 ? EMERALD : AMBER);
            g.setFont(PEER_FONT);
            g.drawString(peer, x, BASELINE);
        }
    }

    /**
     * Abweichung des eigenen Verbrauchs vom Community-Schnitt in Prozent, gerundet.
     * {@code null} ohne Verbrauch oder ohne Peers. Gleiche Rechnung wie im grossen
     * Vorschaubild, damit beide dieselbe Zahl zeigen.
     */
    public static Integer peerDeltaPercent(PublicCarResponse car) {
        if (car == null || car.avgConsumptionKwhPer100km() == null) return null;
        PublicCarResponse.PeerComparison peer = car.peerComparison();
        if (peer == null || peer.peerAvgConsumptionKwhPer100km() == null || peer.peerAvgConsumptionKwhPer100km().signum() <= 0) {
            return null;
        }
        return car.avgConsumptionKwhPer100km().subtract(peer.peerAvgConsumptionKwhPer100km())
                .multiply(BigDecimal.valueOf(100))
                .divide(peer.peerAvgConsumptionKwhPer100km(), 0, RoundingMode.HALF_UP)
                .intValue();
    }

    /** Kuerzt {@code text} mit Ellipse, bis er in {@code maxWidth} Pixel passt. */
    private static String fit(String text, FontMetrics fm, int maxWidth) {
        if (fm.stringWidth(text) <= maxWidth) return text;
        String ellipsis = "…";
        int end = text.length();
        while (end > 1 && fm.stringWidth(text.substring(0, end).stripTrailing() + ellipsis) > maxWidth) end--;
        return text.substring(0, end).stripTrailing() + ellipsis;
    }

    private String num(BigDecimal value, int decimals) {
        return String.format(NUM, "%,." + decimals + "f", value.setScale(decimals, RoundingMode.HALF_UP));
    }
}
