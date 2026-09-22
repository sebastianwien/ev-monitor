package com.evmonitor.infrastructure.image;

import com.evmonitor.application.PublicCarResponse;
import com.evmonitor.infrastructure.image.PublicImageStyle.Lang;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static com.evmonitor.infrastructure.image.PublicImageStyle.*;

/**
 * Vorschaubild (1200x630) der geteilten Fahrzeugseite fuer Link-Karten in
 * Messengern und sozialen Netzen. Gleiche Optik wie die Seite: Papier, Tinte,
 * Barlow Condensed, Masthead oben, Kennzahlen als Zeile unten.
 *
 * Die eine Zeile, die zaehlt, ist der Vergleich zum Modell-Schnitt: sie steht
 * gross in der Mitte. Ohne Peers bleibt die Zeile weg, das Bild traegt dann
 * nur die eigenen Kennzahlen.
 */
@Component
public class SharedCarImageRenderer {

    private static final Logger log = LoggerFactory.getLogger(SharedCarImageRenderer.class);

    public static final int WIDTH = 1200;
    public static final int HEIGHT = 630;
    private static final int MARGIN = 64;

    public byte[] render(PublicCarResponse car) {
        return render(car, Lang.DE);
    }

    /** @return PNG-Bytes oder {@code null}, wenn das Rendern fehlschlaegt. */
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
                paintMasthead(g, lang);
                paintHeader(g, car, lang);
                paintComparison(g, car, lang);
                paintMetrics(g, car, lang);
            } finally {
                g.dispose();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            log.warn("Vorschaubild der Fahrzeugseite fehlgeschlagen: {}", e.getMessage());
            return null;
        }
    }

    /** Wortmarke links, Claim rechts, Linie darunter - wie der Masthead der Seite. */
    private void paintMasthead(Graphics2D g, Lang lang) {
        g.setColor(INK);
        Font brand = display(40f);
        g.setFont(brand);
        g.drawString("EV MONITOR", MARGIN, 76);

        g.setColor(INK_3);
        Font claimFont = text(20f, false);
        g.setFont(claimFont);
        String claim = lang.claim();
        g.drawString(claim, WIDTH - MARGIN - g.getFontMetrics(claimFont).stringWidth(claim), 74);

        g.setColor(INK);
        g.fillRect(MARGIN, 94, WIDTH - 2 * MARGIN, 2);
    }

    private void paintHeader(Graphics2D g, PublicCarResponse car, Lang lang) {
        g.setColor(INK);
        Font title = display(84f);
        g.setFont(title);
        String model = car.carModel() != null ? car.carModel() : lang.fallbackModel();
        g.drawString(SharedCarBannerRenderer.fit(model, g.getFontMetrics(title), WIDTH - 2 * MARGIN), MARGIN, 186);

        List<String> sub = new ArrayList<>();
        if (car.year() != null) sub.add(String.valueOf(car.year()));
        if (car.totalCharges() != null) sub.add(lang.charges(car.totalCharges()));
        if (car.totalDistanceKm() != null) sub.add(lang.num(car.totalDistanceKm(), 0) + " km");
        if (!sub.isEmpty()) {
            g.setColor(INK_2);
            g.setFont(text(24f, false));
            g.drawString(String.join("   ", sub), MARGIN, 226);
        }
    }

    /** Verbrauch gross, darunter die Einordnung gegen den Modell-Schnitt. */
    private void paintComparison(Graphics2D g, PublicCarResponse car, Lang lang) {
        if (car.avgConsumptionKwhPer100km() == null) return;

        g.setColor(INK);
        Font big = display(150f);
        g.setFont(big);
        String value = lang.num(car.avgConsumptionKwhPer100km(), 1);
        g.drawString(value, MARGIN - 4, 382);
        int valueWidth = g.getFontMetrics(big).stringWidth(value);

        g.setColor(INK_2);
        g.setFont(text(30f, false));
        g.drawString("kWh/100km", MARGIN + valueWidth + 14, 382);

        Integer delta = SharedCarBannerRenderer.peerDeltaPercent(car);
        if (delta == null) return;
        PublicCarResponse.PeerComparison peer = car.peerComparison();

        g.setColor(delta <= 0 ? GOOD : WARN);
        g.setFont(text(34f, true));
        g.drawString(lang.peerHeadline(delta, "SPEC".equals(peer.matchType())), MARGIN, 434);

        g.setColor(INK_3);
        g.setFont(text(22f, false));
        g.drawString(lang.peerDetail(lang.num(peer.peerAvgConsumptionKwhPer100km(), 1), peer.peerUsers()), MARGIN, 468);
    }

    /** Die uebrigen Kennzahlen als Reihe am unteren Rand, wie das Datenblatt der Seite. */
    private void paintMetrics(Graphics2D g, PublicCarResponse car, Lang lang) {
        record Metric(String label, String value) {}
        List<Metric> metrics = new ArrayList<>();
        if (car.costPer100km() != null) metrics.add(new Metric(lang.labelCostPer100(), lang.num(car.costPer100km(), 2) + " €"));
        if (car.totalDistanceKm() != null) metrics.add(new Metric(lang.labelDistance(), lang.num(car.totalDistanceKm(), 0) + " km"));
        if (car.publicChargingSharePercent() != null) metrics.add(new Metric(lang.labelPublicShare(), lang.num(car.publicChargingSharePercent(), 0) + " %"));
        if (car.totalKwhCharged() != null) metrics.add(new Metric(lang.labelCharged(), lang.num(car.totalKwhCharged(), 0) + " kWh"));
        if (metrics.isEmpty()) return;

        g.setColor(RULE);
        g.fillRect(MARGIN, 508, WIDTH - 2 * MARGIN, 1);

        Font labelFont = text(17f, false);
        Font valueFont = display(40f);
        int x = MARGIN;
        for (Metric m : metrics) {
            g.setColor(INK_3);
            g.setFont(labelFont);
            g.drawString(m.label(), x, 544);

            g.setColor(INK);
            g.setFont(valueFont);
            g.drawString(m.value(), x, 588);

            // Spalte so breit wie der breitere von Label und Wert, sonst laufen lange Labels ineinander.
            int width = Math.max(g.getFontMetrics(labelFont).stringWidth(m.label()),
                    g.getFontMetrics(valueFont).stringWidth(m.value()));
            x += Math.max(width, 150) + 56;
        }
    }
}
