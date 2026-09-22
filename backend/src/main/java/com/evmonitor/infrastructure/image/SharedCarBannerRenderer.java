package com.evmonitor.infrastructure.image;

import com.evmonitor.application.PublicCarResponse;
import com.evmonitor.infrastructure.image.PublicImageStyle.Lang;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;

import static com.evmonitor.infrastructure.image.PublicImageStyle.*;

/**
 * Forum-Signatur-Banner (468x60) der geteilten Fahrzeugseite. Wird in Foren
 * unter jedem Beitrag geladen und verlinkt auf die Seite - deshalb knapp,
 * gut lesbar und mit denselben Zahlen wie das grosse Vorschaubild.
 *
 * Aufbau von links: Wortmarke, Modell, Verbrauch, Einordnung gegen den
 * Modell-Schnitt. Was nicht mehr in die Breite passt, wird von rechts nach
 * links weggelassen, der Modellname notfalls mit Ellipse gekuerzt. Nichts
 * laeuft ueber den Rand. Optik wie die Seite: Papier, Tinte, durchgehend
 * Barlow Condensed, damit die kleine Flaeche ruhig wirkt.
 */
@Component
public class SharedCarBannerRenderer {

    private static final Logger log = LoggerFactory.getLogger(SharedCarBannerRenderer.class);

    public static final int WIDTH = 468;
    public static final int HEIGHT = 60;
    private static final int PAD = 12;
    private static final int GAP = 10;
    private static final int BASELINE = 38;

    private static final Font BRAND_FONT = display(16f);
    private static final Font MODEL_FONT = display(20f);
    private static final Font VALUE_FONT = display(24f);
    private static final Font UNIT_FONT = displayMedium(13f);
    private static final Font PEER_FONT = displayMedium(13f);
    /** Leichte Sperrung der Wortmarke wie auf der Seite. */
    private static final float BRAND_TRACKING = 0.06f;

    public byte[] render(PublicCarResponse car) {
        return render(car, Lang.DE);
    }

    /** @return PNG-Bytes oder {@code null}, wenn Eingabe fehlt oder das Rendern fehlschlaegt. */
    public byte[] render(PublicCarResponse car, Lang lang) {
        if (car == null) return null;
        try {
            BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
                g.setColor(PAPER);
                g.fillRect(0, 0, WIDTH, HEIGHT);
                g.setColor(INK);
                g.fillRect(0, 0, WIDTH, 2);
                g.fillRect(0, HEIGHT - 1, WIDTH, 1);
                paintContent(g, car, lang);
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

    private void paintContent(Graphics2D g, PublicCarResponse car, Lang lang) {
        int x = PAD;
        int right = WIDTH - PAD;

        // Wortmarke einzeilig und gesperrt, wie im grossen Vorschaubild.
        g.setColor(INK);
        Font brandFont = BRAND_FONT.deriveFont(java.util.Map.of(TextAttribute.TRACKING, BRAND_TRACKING));
        g.setFont(brandFont);
        g.drawString("EV MONITOR", x, BASELINE);
        int brandWidth = g.getFontMetrics(brandFont).stringWidth("EV MONITOR");
        x += brandWidth + GAP;
        g.setColor(RULE);
        g.fillRect(x - GAP / 2, 12, 1, HEIGHT - 24);
        x += GAP / 2;

        // Rechts zuerst reservieren, was fest dazugehoert: Verbrauch und Einordnung.
        // Der Modellname bekommt den Rest und wird gekuerzt.
        String value = car.avgConsumptionKwhPer100km() == null ? null : lang.num(car.avgConsumptionKwhPer100km(), 1);
        String unit = "kWh/100km";
        int valueWidth = value == null ? 0
                : g.getFontMetrics(VALUE_FONT).stringWidth(value) + 4 + g.getFontMetrics(UNIT_FONT).stringWidth(unit);

        Integer delta = peerDeltaPercent(car);
        String peer = delta == null ? null : lang.peerShort(delta);
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
        String model = car.carModel() != null ? car.carModel() : lang.fallbackModel();
        g.setColor(INK);
        g.setFont(MODEL_FONT);
        String shown = fit(model, g.getFontMetrics(MODEL_FONT), modelMax);
        g.drawString(shown, x, BASELINE);
        x += g.getFontMetrics(MODEL_FONT).stringWidth(shown) + GAP;

        if (value != null) {
            g.setColor(INK);
            g.setFont(VALUE_FONT);
            g.drawString(value, x, BASELINE);
            x += g.getFontMetrics(VALUE_FONT).stringWidth(value) + 4;
            g.setColor(INK_3);
            g.setFont(UNIT_FONT);
            g.drawString(unit, x, BASELINE);
            x += g.getFontMetrics(UNIT_FONT).stringWidth(unit) + GAP;
        }

        if (peer != null) {
            g.setColor(delta <= 0 ? GOOD : WARN);
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
    static String fit(String text, FontMetrics fm, int maxWidth) {
        if (fm.stringWidth(text) <= maxWidth) return text;
        String ellipsis = "…";
        int end = text.length();
        while (end > 1 && fm.stringWidth(text.substring(0, end).stripTrailing() + ellipsis) > maxWidth) end--;
        return text.substring(0, end).stripTrailing() + ellipsis;
    }
}
