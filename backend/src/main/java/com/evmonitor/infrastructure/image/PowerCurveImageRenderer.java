package com.evmonitor.infrastructure.image;

import com.evmonitor.application.PowerCurveResponse;
import com.evmonitor.application.PublicCurveResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

/**
 * Zeichnet das Vorschaubild einer geteilten Ladekurve - das, was in Foren,
 * Messengern und sozialen Netzen als Kartenbild erscheint.
 *
 * 1200x630, das Format das Facebook, LinkedIn und X erwarten. Java2D statt einer
 * Rendering-Bibliothek: die Kurve ist ein Polygonzug mit etwas Text daneben,
 * dafuer braucht es keine zusaetzliche Abhaengigkeit.
 *
 * Deterministisch - dieselbe Kurve ergibt dieselben Bytes. Sonst waere das Bild
 * nicht sinnvoll cachebar und Crawler saehen bei jedem Abruf etwas anderes.
 *
 * <p><b>Betrieb:</b> Java2D braucht installierte Fonts. Das JRE-Image bringt keine
 * mit, deshalb installiert das Dockerfile {@code fontconfig} und
 * {@code fonts-dejavu-core}. Fehlen sie, faellt die Textausgabe aus.
 */
@Component
public class PowerCurveImageRenderer {

    private static final Logger log = LoggerFactory.getLogger(PowerCurveImageRenderer.class);

    public static final int WIDTH = 1200;
    public static final int HEIGHT = 630;

    // Fensterrahmen der Kurve innerhalb des Bildes
    private static final int PLOT_LEFT = 80;
    private static final int PLOT_RIGHT = WIDTH - 80;
    private static final int PLOT_TOP = 300;
    private static final int PLOT_BOTTOM = HEIGHT - 96;

    private static final Color BG = new Color(0x0F172A);
    private static final Color BG_ACCENT = new Color(0x14243F);
    private static final Color EMERALD = new Color(0x10B981);
    private static final Color EMERALD_DARK = new Color(0x059669);
    private static final Color TEXT = new Color(0xF1F5F9);
    private static final Color TEXT_MUTED = new Color(0x94A3B8);
    private static final Color GRID = new Color(0x1E293B);

    /** Deutsches Zahlenformat - Hauptmarkt, und das Bild gibt es pro Token nur einmal. */
    private static final Locale NUM = Locale.GERMANY;

    /**
     * @return PNG-Bytes, oder {@code null} wenn die Kurve keine Punkte hat oder
     *         das Rendern fehlschlaegt. Der Aufrufer liefert dann kein Bild aus -
     *         eine kaputte Vorschau ist schlechter als gar keine.
     */
    public byte[] render(PublicCurveResponse curve) {
        if (curve == null || curve.points() == null || curve.points().isEmpty()) return null;

        try {
            BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

                paintBackground(g);
                paintHeader(g, curve);
                paintMetrics(g, curve);
                paintCurve(g, curve.points());
                paintFooter(g);
            } finally {
                g.dispose();
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            log.warn("Vorschaubild der Ladekurve fehlgeschlagen: {}", e.getMessage());
            return null;
        }
    }

    private void paintBackground(Graphics2D g) {
        g.setPaint(new GradientPaint(0, 0, BG_ACCENT, 0, HEIGHT, BG));
        g.fillRect(0, 0, WIDTH, HEIGHT);
    }

    private void paintHeader(Graphics2D g, PublicCurveResponse curve) {
        g.setColor(EMERALD);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
        g.drawString("EV-MONITOR", PLOT_LEFT, 72);

        g.setColor(TEXT);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 64));
        g.drawString(curve.carModel() != null ? curve.carModel() : "Ladekurve", PLOT_LEFT, 152);

        String subtitle = subtitle(curve);
        if (!subtitle.isEmpty()) {
            g.setColor(TEXT_MUTED);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 28));
            g.drawString(subtitle, PLOT_LEFT, 196);
        }
    }

    /** Ladeort und Datum - der Kontext, ohne den eine Kurve schwer einzuordnen ist. */
    private String subtitle(PublicCurveResponse curve) {
        StringBuilder sb = new StringBuilder();
        if (curve.cpoName() != null && !curve.cpoName().isBlank()) sb.append(curve.cpoName());
        if (curve.chargedOn() != null) {
            if (sb.length() > 0) sb.append("  ·  ");
            sb.append(String.format(NUM, "%1$td.%1$tm.%1$tY", curve.chargedOn()));
        }
        return sb.toString();
    }

    /** Die Zahlen, um die in Foren gestritten wird - als Reihe direkt unter dem Titel. */
    private void paintMetrics(Graphics2D g, PublicCurveResponse curve) {
        record Metric(String label, String value) {}
        List<Metric> metrics = new java.util.ArrayList<>();

        if (curve.peakKw() != null) metrics.add(new Metric("SPITZE", num(curve.peakKw(), 0) + " kW"));
        if (curve.kwhCharged() != null) metrics.add(new Metric("GELADEN", num(curve.kwhCharged(), 1) + " kWh"));
        if (curve.durationMinutes() != null) metrics.add(new Metric("DAUER", curve.durationMinutes() + " Min"));
        if (curve.socBefore() != null && curve.socAfter() != null) {
            metrics.add(new Metric("LADESTAND",
                    num(curve.socBefore(), 0) + " → " + num(curve.socAfter(), 0) + " %"));
        }
        if (metrics.isEmpty()) return;

        int x = PLOT_LEFT;
        for (Metric m : metrics) {
            g.setColor(TEXT_MUTED);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18));
            g.drawString(m.label(), x, 246);

            g.setColor(TEXT);
            Font valueFont = new Font(Font.SANS_SERIF, Font.BOLD, 34);
            g.setFont(valueFont);
            g.drawString(m.value(), x, 282);

            int width = g.getFontMetrics(valueFont).stringWidth(m.value());
            x += Math.max(width, 140) + 56;
        }
    }

    private void paintCurve(Graphics2D g, List<PowerCurveResponse.Point> points) {
        double maxKw = points.stream().mapToDouble(PowerCurveResponse.Point::kw).max().orElse(1);
        if (maxKw <= 0) maxKw = 1;
        // 8 % Luft nach oben, damit die Spitze nicht am Rahmen klebt.
        double scaleMax = maxKw * 1.08;

        long first = points.get(0).ts();
        long span = points.get(points.size() - 1).ts() - first;

        int plotW = PLOT_RIGHT - PLOT_LEFT;
        int plotH = PLOT_BOTTOM - PLOT_TOP;

        g.setColor(GRID);
        g.setStroke(new BasicStroke(1f));
        for (int i = 0; i <= 4; i++) {
            int y = PLOT_TOP + (plotH * i) / 4;
            g.drawLine(PLOT_LEFT, y, PLOT_RIGHT, y);
        }

        // Ein einzelner Punkt hat keine Zeitspanne - als waagerechte Linie zeichnen,
        // sonst entstuende eine Division durch null.
        GeneralPath stroke = new GeneralPath();
        int[] xs = new int[points.size()];
        int[] ys = new int[points.size()];
        for (int i = 0; i < points.size(); i++) {
            PowerCurveResponse.Point p = points.get(i);
            double xRatio = span > 0 ? (double) (p.ts() - first) / span : (points.size() == 1 ? 0 : (double) i / (points.size() - 1));
            xs[i] = PLOT_LEFT + (int) Math.round(xRatio * plotW);
            ys[i] = PLOT_BOTTOM - (int) Math.round((p.kw() / scaleMax) * plotH);
            if (i == 0) stroke.moveTo(xs[i], ys[i]);
            else stroke.lineTo(xs[i], ys[i]);
        }
        if (points.size() == 1) stroke.lineTo(PLOT_RIGHT, ys[0]);

        GeneralPath fill = new GeneralPath(stroke);
        fill.lineTo(points.size() == 1 ? PLOT_RIGHT : xs[xs.length - 1], PLOT_BOTTOM);
        fill.lineTo(PLOT_LEFT, PLOT_BOTTOM);
        fill.closePath();

        // Verlauf von halbtransparent unter der Linie nach voellig klar am Boden.
        g.setPaint(new GradientPaint(0, PLOT_TOP, new Color(16, 185, 129, 110),
                0, PLOT_BOTTOM, new Color(16, 185, 129, 0)));
        g.fill(fill);

        g.setColor(EMERALD);
        g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(stroke);

        g.setColor(EMERALD_DARK);
        g.fillOval(xs[0] - 8, ys[0] - 8, 16, 16);
        g.setColor(TEXT);
        g.fillOval(xs[0] - 4, ys[0] - 4, 8, 8);
    }

    private void paintFooter(Graphics2D g) {
        g.setColor(TEXT_MUTED);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 22));
        g.drawString("Echte Ladekurve, aufgezeichnet mit ev-monitor.net", PLOT_LEFT, HEIGHT - 40);
    }

    private String num(BigDecimal value, int decimals) {
        return String.format(NUM, "%,." + decimals + "f", value.setScale(decimals, RoundingMode.HALF_UP));
    }
}
