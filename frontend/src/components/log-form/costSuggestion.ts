/**
 * Entscheidet, ob das Umschalten von privat/oeffentlich (bzw. AC->DC, das
 * oeffentlich erzwingt) den aktuellen Preis verwerfen und einen frischen
 * Preisvorschlag holen darf.
 *
 * Kernregel: Ein bereits vorhandener Preis - egal ob manuell eingegeben oder
 * aus einem gespeicherten Log abgeleitet - darf NIEMALS ueberschrieben werden.
 * Ein Vorschlag fuellt ausschliesslich leere Felder. Ohne diese Regel loeschte
 * der Toggle auf Mobile (wo per GPS ein Standort gesetzt ist) den gerade
 * eingegebenen Preis.
 */
export function shouldRefetchPriceOnToggle(params: {
  hasLocation: boolean
  costLocalTotal: number | null
  costLocalPerKwh: number | null
  costEur: number | null
}): boolean {
  if (!params.hasLocation) return false
  if (params.costLocalTotal != null) return false
  if (params.costLocalPerKwh != null) return false
  if (params.costEur != null) return false
  return true
}
