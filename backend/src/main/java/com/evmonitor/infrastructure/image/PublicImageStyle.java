package com.evmonitor.infrastructure.image;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.awt.Font;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * Gemeinsame Optik der Bilder zur geteilten Fahrzeugseite (Vorschaubild, Banner):
 * dieselbe Papier-Farbwelt und dieselbe Display-Schrift wie die Seite selbst,
 * damit Link-Karte, Forum-Banner und Seite als ein Stueck wirken.
 *
 * <p>Farben entsprechen den {@code --pc-*}-Variablen in {@code PublicCarView.vue}
 * (Light Mode, Kontrast auf WCAG AA gerechnet). Barlow Condensed liegt als TTF
 * unter {@code fonts/}, OFL, dieselbe Familie wie das woff2 des Frontends.
 */
public final class PublicImageStyle {

    private static final Logger log = LoggerFactory.getLogger(PublicImageStyle.class);

    static final Color PAPER = new Color(0xF7F6F2);
    static final Color INK = new Color(0x14161A);
    static final Color INK_2 = new Color(0x4B4F58);
    static final Color INK_3 = new Color(0x666B75);
    static final Color RULE = new Color(0xD9D7D0);
    static final Color BAND = new Color(0xECEAE2);
    static final Color GOOD = new Color(0x0B7350);
    static final Color WARN = new Color(0x9A5A05);

    private static final Font DISPLAY_BASE = load("/fonts/BarlowCondensed-Bold.ttf", Font.BOLD);
    private static final Font DISPLAY_MEDIUM_BASE = load("/fonts/BarlowCondensed-SemiBold.ttf", Font.BOLD);

    private PublicImageStyle() {}

    /** Wortmarke, Titel, grosse Zahlen. */
    static Font display(float size) {
        return DISPLAY_BASE.deriveFont(size);
    }

    /** Etwas leichtere Display-Schnitt fuer Untertitel. */
    static Font displayMedium(float size) {
        return DISPLAY_MEDIUM_BASE.deriveFont(size);
    }

    /** Fliesstext, systemeigene Sans wie auf der Seite. */
    static Font text(float size, boolean bold) {
        return new Font(Font.SANS_SERIF, bold ? Font.BOLD : Font.PLAIN, Math.round(size));
    }

    private static Font load(String resource, int fallbackStyle) {
        try (InputStream in = PublicImageStyle.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("Schrift fehlt: " + resource);
            return Font.createFont(Font.TRUETYPE_FONT, in);
        } catch (Exception e) {
            log.warn("Display-Schrift {} nicht ladbar, Fallback auf Sans: {}", resource, e.getMessage());
            return new Font(Font.SANS_SERIF, fallbackStyle, 12);
        }
    }

    /**
     * Sprache der Bildtexte. Die Bilder haben keinen Browser-Kontext, deshalb
     * kommt die Sprache als Parameter aus der Seite bzw. der Signatur.
     */
    public enum Lang {
        DE("de", Locale.GERMANY), EN("en", Locale.UK), NB("nb", Locale.forLanguageTag("nb-NO")), SV("sv", Locale.forLanguageTag("sv-SE"));

        public final String code;
        final Locale numbers;

        Lang(String code, Locale numbers) {
            this.code = code;
            this.numbers = numbers;
        }

        /** Unbekanntes oder fehlendes Kuerzel faellt auf Deutsch zurueck. */
        public static Lang of(String code) {
            if (code == null) return DE;
            for (Lang l : values()) if (l.code.equalsIgnoreCase(code.trim())) return l;
            return DE;
        }

        String num(BigDecimal value, int decimals) {
            return String.format(numbers, "%,." + decimals + "f", value.setScale(decimals, RoundingMode.HALF_UP));
        }

        String claim() {
            return switch (this) {
                case DE -> "Echter Verbrauch statt WLTP";
                case EN -> "Real consumption, not WLTP";
                case NB -> "Faktisk forbruk, ikke WLTP";
                case SV -> "Verklig förbrukning, inte WLTP";
            };
        }

        String fallbackModel() {
            return switch (this) {
                case DE -> "Fahrzeug";
                case EN -> "Vehicle";
                case NB -> "Kjøretøy";
                case SV -> "Fordon";
            };
        }

        String charges(int n) {
            return switch (this) {
                case DE -> n + (n == 1 ? " Ladung" : " Ladungen");
                case EN -> n + (n == 1 ? " charge" : " charges");
                case NB -> n + (n == 1 ? " lading" : " ladinger");
                case SV -> n + (n == 1 ? " laddning" : " laddningar");
            };
        }

        /** Eine Zeile: Abweichung vom Schnitt, {@code spec} = Vergleich auf Variantenebene. */
        String peerHeadline(int delta, boolean spec) {
            String scope = switch (this) {
                case DE -> spec ? "dieser Variante" : "dieses Modells";
                case EN -> spec ? "for this variant" : "for this model";
                case NB -> spec ? "for denne varianten" : "for denne modellen";
                case SV -> spec ? "för den här varianten" : "för den här modellen";
            };
            int pct = Math.abs(delta);
            return switch (this) {
                case DE -> delta == 0 ? "Genau im Schnitt " + scope : pct + " % " + (delta < 0 ? "unter" : "über") + " dem Schnitt " + scope;
                case EN -> delta == 0 ? "Right on the average " + scope : pct + " % " + (delta < 0 ? "below" : "above") + " average " + scope;
                case NB -> delta == 0 ? "Akkurat på snittet " + scope : pct + " % " + (delta < 0 ? "under" : "over") + " snittet " + scope;
                case SV -> delta == 0 ? "Precis på snittet " + scope : pct + " % " + (delta < 0 ? "under" : "över") + " snittet " + scope;
            };
        }

        /** Kurzform fuer das Banner. */
        String peerShort(int delta) {
            int pct = Math.abs(delta);
            return switch (this) {
                case DE -> delta == 0 ? "im Schnitt" : pct + " % " + (delta < 0 ? "unter" : "über") + " Schnitt";
                case EN -> delta == 0 ? "on average" : pct + " % " + (delta < 0 ? "below" : "above") + " average";
                case NB -> delta == 0 ? "på snittet" : pct + " % " + (delta < 0 ? "under" : "over") + " snittet";
                case SV -> delta == 0 ? "på snittet" : pct + " % " + (delta < 0 ? "under" : "över") + " snittet";
            };
        }

        String peerDetail(String avg, int others) {
            return switch (this) {
                case DE -> "Community-Schnitt " + avg + " kWh/100km · " + others + (others == 1 ? " anderer Fahrer" : " andere Fahrer");
                case EN -> "Community average " + avg + " kWh/100km · " + others + (others == 1 ? " other driver" : " other drivers");
                case NB -> "Fellesskapets snitt " + avg + " kWh/100km · " + others + (others == 1 ? " annen sjåfør" : " andre sjåfører");
                case SV -> "Gemenskapens snitt " + avg + " kWh/100km · " + others + (others == 1 ? " annan förare" : " andra förare");
            };
        }

        String labelCostPer100() {
            return switch (this) { case DE -> "Kosten / 100 km"; case EN -> "Cost / 100 km"; case NB -> "Kostnad / 100 km"; case SV -> "Kostnad / 100 km"; };
        }

        String labelDistance() {
            return switch (this) { case DE -> "Strecke"; case EN -> "Distance"; case NB -> "Distanse"; case SV -> "Sträcka"; };
        }

        String labelPublicShare() {
            return switch (this) { case DE -> "Öffentlich geladen"; case EN -> "Public charging"; case NB -> "Offentlig lading"; case SV -> "Publik laddning"; };
        }

        String labelCharged() {
            return switch (this) { case DE -> "Geladen"; case EN -> "Charged"; case NB -> "Ladet"; case SV -> "Laddat"; };
        }
    }
}
