/**
 * Farben der Kostenkategorien, gleich auf Dashboard und Fahrzeugseite: Strom Indigo, Fixkosten
 * Abstufungen von Amber, die groesste Kategorie des Jahres am dunkelsten.
 */
const FIXED_SHADES = ['#d97706', '#f59e0b', '#fbbf24', '#fcd34d', '#fde68a', '#b45309']

/** `ranked` aus `rankCategories`, Strom-Schluessel siehe `ENERGY` in `useYearCosts`. */
export function costColor(key: string, ranked: readonly string[], isDark: boolean, energyKey: string): string {
  if (key === energyKey) return isDark ? '#818cf8' : '#4f46e5'
  return FIXED_SHADES[Math.max(0, ranked.indexOf(key)) % FIXED_SHADES.length]
}
