/**
 * Spaltenraster der Desktop-Feed-Zeilen (Tailwind-Klassen).
 *
 * Fahrtgruppen-Kopf, Fahrtzeilen, Ladegruppen, Teilladungen und Ladezeilen teilen sich
 * dieses Template, damit ihre Werte ueber alle Zeilentypen hinweg spaltenweise fluchten.
 * Spalten: Icon | Zeit | Energie | Wert | SoC | Leistung bzw. Tempo | Strecke bzw. Dauer |
 * Temperatur | Preis | Menue. Eine Aenderung hier wirkt auf alle Zeilentypen zugleich.
 */
export const FEED_GRID_COLS =
  'grid grid-cols-[52px_90px_minmax(110px,1fr)_125px_80px_130px_88px_76px_132px_40px] gap-1.5'
