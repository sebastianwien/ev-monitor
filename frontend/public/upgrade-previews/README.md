# Upgrade-View Feature Previews

Vier Screenshots werden in der LIVE-Tier-Karte (`/upgrade`) per Inline-Expand
gezeigt. Wenn der User auf das Chevron neben einem Feature-Bullet klickt,
oeffnet sich der zugehoerige Screenshot direkt darunter.

## Erwartete Dateien

| Datei | Feature | Hinweise |
|---|---|---|
| `live-view.png` | Live-Ansicht beim Laden | Screenshot der `LiveChargingCard` mit aktiver Session (Power-Kurve sichtbar, kW-Wert prominent) |
| `curves.png` | Ladekurven von DC-Sessions | Screenshot der inline-expandierten Ladekurve in `LogsView`, idealerweise mit Tesla-Supercharger-Session |
| `insights.png` | Energie-Split Donut | Screenshot des Donut-Diagramms aus `DashboardInsights` (Energie-Split Tab) |
| `calendar.png` | Fahrten-Kalender | Screenshot der Kalender-Ansicht aus `DashboardInsights` (Fahrten Tab) |

## Format-Empfehlung

- **PNG** (besser komprimierbar als JPG fuer UI-Screenshots mit klaren Kanten)
- **Aufloesung**: 800-1200px Breite reicht, wird per CSS auf Container-Breite skaliert
- **Hintergrund**: Light-Theme verwenden (Dark-Theme-Variante optional, ggf. spaeter)
- **Aspect**: portraet/landscape je nach Feature, max ~600px Hoehe damit der Bullet-Block nicht explodiert

Bilder werden per `<img loading="lazy">` nachgeladen, also keine Performance-Sorge
bei der initialen Page-Load.
