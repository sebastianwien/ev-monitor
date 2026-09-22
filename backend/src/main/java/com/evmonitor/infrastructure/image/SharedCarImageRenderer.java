package com.evmonitor.infrastructure.image;

import com.evmonitor.application.PublicCarResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Vorschaubild (1200x630) der geteilten Fahrzeugseite fuer Link-Karten in
 * Messengern und sozialen Netzen. Gleiche Farbwelt wie die Ladekurven-Karte.
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

    private static final Color BG = new Color(0x0F172A);
    private static final Color BG_ACCENT = new Color(0x14243F);
    private static final Color EMERALD = new Color(0x10B981);
    private static final Color AMBER = new Color(0xF59E0B);
    private static final Color TEXT = new Color(0xF1F5F9);
    private static final Color TEXT_MUTED = new Color(0x94A3B8);
    private static final Color LINE = new Color(0x1E293B);
    private static final Locale NUM = Locale.GERMANY;

    /** @return PNG-Bytes oder {@code null}, wenn das Rendern fehlschlaegt. */
    public byte[] render(PublicCarResponse car) {
        if (car == null) return null;
        try {
            BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setPaint(new GradientPaint(0, 0, BG_ACCENT, 0, HEIGHT, BG));
                g.fillRect(0, 0, WIDTH, HEIGHT);
                paintHeader(g, car);
                paintComparison(g, car);
                paintMetrics(g, car);
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

    private void paintHeader(Graphics2D g, PublicCarResponse car) {
        g.setColor(EMERALD);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
        g.drawString("EV-MONITOR", MARGIN, 60);

        g.setColor(TEXT_MUTED);
        Font creditFont = new Font(Font.SANS_SERIF, Font.PLAIN, 18);
        g.setFont(creditFont);
        String credit = "Echter Verbrauch, aufgezeichnet mit ev-monitor.net";
        g.drawString(credit, WIDTH - MARGIN - g.getFontMetrics(creditFont).stringWidth(credit), 60);

        g.setColor(TEXT);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 48));
        g.drawString(car.carModel() != null ? car.carModel() : "Fahrzeug", MARGIN, 130);

        List<String> sub = new ArrayList<>();
        if (car.year() != null) sub.add(String.valueOf(car.year()));
        if (car.totalCharges() != null) sub.add(car.totalCharges() + " Ladungen");
        if (!sub.isEmpty()) {
            g.setColor(TEXT_MUTED);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 24));
            g.drawString(String.join("  ·  ", sub), MARGIN, 170);
        }
    }

    /** Verbrauch gross, daneben die Einordnung gegen den Modell-Schnitt. */
    private void paintComparison(Graphics2D g, PublicCarResponse car) {
        if (car.avgConsumptionKwhPer100km() == null) return;

        g.setColor(TEXT);
        Font big = new Font(Font.SANS_SERIF, Font.BOLD, 96);
        g.setFont(big);
        String value = num(car.avgConsumptionKwhPer100km(), 1);
        g.drawString(value, MARGIN, 320);
        int valueWidth = g.getFontMetrics(big).stringWidth(value);

        g.setColor(TEXT_MUTED);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 30));
        g.drawString("kWh/100km", MARGIN + valueWidth + 18, 320);

        Integer delta = SharedCarBannerRenderer.peerDeltaPercent(car);
        if (delta == null) return;
        PublicCarResponse.PeerComparison peer = car.peerComparison();
        String scope = "SPEC".equals(peer.matchType()) ? "dieser Variante" : "dieses Modells";
        String headline = delta == 0 ? "Genau im Schnitt " + scope
                : Math.abs(delta) + " % " + (delta < 0 ? "unter" : "über") + " dem Schnitt " + scope;

        g.setColor(delta <= 0 ? EMERALD : AMBER);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 36));
        g.drawString(headline, MARGIN, 380);

        g.setColor(TEXT_MUTED);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 22));
        String detail = "Community-Schnitt " + num(peer.peerAvgConsumptionKwhPer100km(), 1) + " kWh/100km · "
                + peer.peerUsers() + (peer.peerUsers() == 1 ? " anderer Fahrer" : " andere Fahrer");
        g.drawString(detail, MARGIN, 414);
    }

    /** Die uebrigen Kennzahlen als Reihe am unteren Rand. */
    private void paintMetrics(Graphics2D g, PublicCarResponse car) {
        record Metric(String label, String value) {}
        List<Metric> metrics = new ArrayList<>();
        if (car.costPer100km() != null) metrics.add(new Metric("KOSTEN / 100 KM", num(car.costPer100km(), 2) + " €"));
        if (car.totalDistanceKm() != null) metrics.add(new Metric("STRECKE", num(car.totalDistanceKm(), 0) + " km"));
        if (car.publicChargingSharePercent() != null) metrics.add(new Metric("ÖFFENTLICH GELADEN", num(car.publicChargingSharePercent(), 0) + " %"));
        if (car.totalKwhCharged() != null) metrics.add(new Metric("GELADEN", num(car.totalKwhCharged(), 0) + " kWh"));
        if (metrics.isEmpty()) return;

        g.setColor(LINE);
        g.fillRect(MARGIN, 470, WIDTH - 2 * MARGIN, 2);

        Font labelFont = new Font(Font.SANS_SERIF, Font.PLAIN, 16);
        Font valueFont = new Font(Font.SANS_SERIF, Font.BOLD, 30);
        int x = MARGIN;
        for (Metric m : metrics) {
            g.setColor(TEXT_MUTED);
            g.setFont(labelFont);
            g.drawString(m.label(), x, 516);

            g.setColor(TEXT);
            g.setFont(valueFont);
            g.drawString(m.value(), x, 552);

            // Spalte so breit wie der breitere von Label und Wert, sonst laufen lange Labels ineinander.
            int width = Math.max(g.getFontMetrics(labelFont).stringWidth(m.label()),
                    g.getFontMetrics(valueFont).stringWidth(m.value()));
            x += Math.max(width, 150) + 56;
        }
    }

    private String num(BigDecimal value, int decimals) {
        return String.format(NUM, "%,." + decimals + "f", value.setScale(decimals, RoundingMode.HALF_UP));
    }
}
